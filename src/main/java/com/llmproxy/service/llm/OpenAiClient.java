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
public class OpenAiClient implements LlmClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiClient.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    
    @Value("${api.openai.key}")
    private String apiKey;
    
    private final ObjectMapper objectMapper;
    private final ModelVersionValidator modelVersionValidator;
    private final TokenEstimator tokenEstimator;
    private final RestClient restClient;
    
    @Override
    public ModelType getModelType() {
        return ModelType.OPENAI;
    }
    
    @Override
    @Retry(name = "llmRetry")
    public QueryResult query(String query, String modelVersion) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw ModelError.apiKeyMissingError(ModelType.OPENAI.toString());
        }
        
        long startTime = Instant.now().toEpochMilli();
        String validModelVersion = modelVersionValidator.validateModelVersion(ModelType.OPENAI, modelVersion);
        
        QueryResult result = QueryResult.builder()
                .numRetries(0)
                .build();
        
        if (apiKey.startsWith("test_")) {
            logger.info("Using test OpenAI key, returning simulated response");
            
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            String response = "This is a simulated response for testing purposes. The actual OpenAI model is currently unavailable.";
            result.setStatusCode(HttpStatus.OK.value());
            result.setResponse(response);
            tokenEstimator.estimateTokens(result, query, response);
            result.setResponseTimeMs(Instant.now().toEpochMilli() - startTime);
            
            return result;
        }
        
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", validModelVersion);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 150);
            
            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            message.put("content", query);
            
            String responseBody = restClient.post()
                .uri(API_URL)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(requestBody.toString())
                .retrieve()
                .onStatus(status -> status.equals(HttpStatus.TOO_MANY_REQUESTS), 
                    (request, response) -> { throw ModelError.rateLimitError(ModelType.OPENAI.toString()); })
                .onStatus(status -> status.value() >= 400 && status.value() < 500,
                    (request, response) -> {
                        JsonNode errorNode = objectMapper.readTree(response.getBody());
                        String errorMessage = errorNode.path("error").path("message").asText("API error");
                        throw new ModelError(ModelType.OPENAI.toString(), response.getStatusCode().value(), errorMessage, false);
                    })
                .onStatus(status -> status.value() >= 500,
                    (request, response) -> {
                        JsonNode errorNode = objectMapper.readTree(response.getBody());
                        String errorMessage = errorNode.path("error").path("message").asText("API error");
                        throw new ModelError(ModelType.OPENAI.toString(), response.getStatusCode().value(), errorMessage, true);
                    })
                .body(String.class);
            
            JsonNode responseNode = objectMapper.readTree(responseBody);
            JsonNode choicesNode = responseNode.path("choices");
            
            if (choicesNode.isEmpty()) {
                throw ModelError.emptyResponseError(ModelType.OPENAI.toString());
            }
            
            String responseText = choicesNode.path(0).path("message").path("content").asText();
            
            JsonNode usageNode = responseNode.path("usage");
            int promptTokens = usageNode.path("prompt_tokens").asInt(0);
            int completionTokens = usageNode.path("completion_tokens").asInt(0);
            int totalTokens = usageNode.path("total_tokens").asInt(0);
            
            result.setResponse(responseText);
            result.setStatusCode(HttpStatus.OK.value());
            result.setInputTokens(promptTokens);
            result.setOutputTokens(completionTokens);
            result.setTotalTokens(totalTokens);
            result.setNumTokens(totalTokens); // For backward compatibility
            
        } catch (ModelError e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error querying OpenAI: {}", e.getMessage(), e);
            throw ModelError.invalidResponseError(ModelType.OPENAI.toString(), e);
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
            logger.info("Using test OpenAI key, assuming service is available");
            return true;
        }
        
        try {
            restClient.get()
                .uri("https://api.openai.com/v1/models")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            logger.error("Error checking OpenAI availability: {}", e.getMessage());
            return false;
        }
    }
}
