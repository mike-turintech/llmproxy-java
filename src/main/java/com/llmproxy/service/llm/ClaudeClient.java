package com.llmproxy.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.llmproxy.exception.ModelError;
import com.llmproxy.model.ModelType;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ClaudeClient implements LlmClient {
    private static final Logger logger = LoggerFactory.getLogger(ClaudeClient.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    
    @Value("${api.claude.key}")
    private String apiKey;
    
    private final ObjectMapper objectMapper;
    private final ModelVersionValidator modelVersionValidator;
    private final TokenEstimator tokenEstimator;
    private final RestClient restClient;
    
    @Override
    public ModelType getModelType() {
        return ModelType.CLAUDE;
    }
    
    @Override
    @Retry(name = "llmRetry")
    public QueryResult query(String query, String modelVersion) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw ModelError.apiKeyMissingError(ModelType.CLAUDE.toString());
        }
        
        long startTime = Instant.now().toEpochMilli();
        String validModelVersion = modelVersionValidator.validateModelVersion(ModelType.CLAUDE, modelVersion);
        
        QueryResult result = QueryResult.builder()
                .numRetries(0)
                .build();
        
        if (apiKey.startsWith("test_")) {
            logger.info("Using test Claude key, returning simulated response");
            
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            String response = "This is a simulated response for testing purposes. The actual Claude model is currently unavailable.";
            result.setStatusCode(HttpStatus.OK.value());
            result.setResponse(response);
            tokenEstimator.estimateTokens(result, query, response);
            result.setResponseTimeMs(Instant.now().toEpochMilli() - startTime);
            
            return result;
        }
        
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", validModelVersion);
            requestBody.put("max_tokens", 150);
            requestBody.put("temperature", 0.7);
            
            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            message.put("content", query);
            
            String responseBody = restClient.post()
                .uri(API_URL)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("anthropic-version", "2023-06-01")
                .body(requestBody.toString())
                .retrieve()
                .onStatus(status -> status.equals(HttpStatus.TOO_MANY_REQUESTS), 
                    (request, response) -> { throw ModelError.rateLimitError(ModelType.CLAUDE.toString()); })
                .onStatus(status -> status.value() >= 400 && status.value() < 500,
                    (request, response) -> {
                        JsonNode errorNode = objectMapper.readTree(response.getBody());
                        String errorMessage = errorNode.path("error").path("message").asText("API error");
                        throw new ModelError(ModelType.CLAUDE.toString(), response.getStatusCode().value(), errorMessage, false);
                    })
                .onStatus(status -> status.value() >= 500,
                    (request, response) -> {
                        JsonNode errorNode = objectMapper.readTree(response.getBody());
                        String errorMessage = errorNode.path("error").path("message").asText("API error");
                        throw new ModelError(ModelType.CLAUDE.toString(), response.getStatusCode().value(), errorMessage, true);
                    })
                .body(String.class);
            
            JsonNode responseNode = objectMapper.readTree(responseBody);
            JsonNode contentNode = responseNode.path("content");
            
            if (contentNode.isEmpty()) {
                throw ModelError.emptyResponseError(ModelType.CLAUDE.toString());
            }
            
            String responseText = contentNode.path(0).path("text").asText();
            
            int inputTokens = responseNode.path("usage").path("input_tokens").asInt(0);
            int outputTokens = responseNode.path("usage").path("output_tokens").asInt(0);
            int totalTokens = inputTokens + outputTokens;
            
            result.setResponse(responseText);
            result.setStatusCode(HttpStatus.OK.value());
            result.setInputTokens(inputTokens);
            result.setOutputTokens(outputTokens);
            result.setTotalTokens(totalTokens);
            result.setNumTokens(totalTokens); // For backward compatibility
            
        } catch (ModelError e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error querying Claude: {}", e.getMessage(), e);
            throw ModelError.invalidResponseError(ModelType.CLAUDE.toString(), e);
        } finally {
            result.setResponseTimeMs(Instant.now().toEpochMilli() - startTime);
        }
        
        return result;
    }
    
    @Override
    public boolean checkAvailability() {
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }
        
        if (apiKey.startsWith("test_")) {
            logger.info("Using test Claude key, assuming service is available");
            return true;
        }
        
        try {
            restClient.get()
                .uri("https://api.anthropic.com/v1/models")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("anthropic-version", "2023-06-01")
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            logger.error("Error checking Claude availability: {}", e.getMessage());
            return false;
        }
    }
}
