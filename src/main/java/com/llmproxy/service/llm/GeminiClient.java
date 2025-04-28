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
public class GeminiClient implements LlmClient {
    private static final Logger logger = LoggerFactory.getLogger(GeminiClient.class);
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1/models/";
    
    @Value("${api.gemini.key}")
    private String apiKey;
    
    private final ObjectMapper objectMapper;
    private final ModelVersionValidator modelVersionValidator;
    private final TokenEstimator tokenEstimator;
    private final RestClient restClient;
    
    @Override
    public ModelType getModelType() {
        return ModelType.GEMINI;
    }
    
    @Override
    @Retry(name = "llmRetry")
    public QueryResult query(String query, String modelVersion) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw ModelError.apiKeyMissingError(ModelType.GEMINI.toString());
        }
        
        long startTime = Instant.now().toEpochMilli();
        String validModelVersion = modelVersionValidator.validateModelVersion(ModelType.GEMINI, modelVersion);
        
        QueryResult result = QueryResult.builder()
                .numRetries(0)
                .build();
        
        if (apiKey.startsWith("test_")) {
            logger.info("Using test Gemini key, returning simulated response");
            
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            String response = "This is a simulated response for testing purposes. The actual Gemini model is currently unavailable.";
            result.setStatusCode(HttpStatus.OK.value());
            result.setResponse(response);
            tokenEstimator.estimateTokens(result, query, response);
            result.setResponseTimeMs(Instant.now().toEpochMilli() - startTime);
            
            return result;
        }
        
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            
            ObjectNode contents = requestBody.putObject("contents");
            ArrayNode parts = contents.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", query);
            
            requestBody.put("temperature", 0.7);
            requestBody.put("maxOutputTokens", 150);
            
            String fullUrl = API_URL + validModelVersion + ":generateContent?key=" + apiKey;
            String responseBody = restClient.post()
                .uri(fullUrl)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(requestBody.toString())
                .retrieve()
                .onStatus(status -> status.equals(HttpStatus.TOO_MANY_REQUESTS), 
                    (request, response) -> { throw ModelError.rateLimitError(ModelType.GEMINI.toString()); })
                .onStatus(status -> status.is4xxError() || status.is5xxError(),
                    (request, response) -> {
                        JsonNode errorNode = objectMapper.readTree(response.getBody());
                        String errorMessage = errorNode.path("error").path("message").asText("API error");
                        boolean retryable = status.is5xxError();
                        throw new ModelError(ModelType.GEMINI.toString(), status.value(), errorMessage, retryable);
                    })
                .body(String.class);
            
            JsonNode responseNode = objectMapper.readTree(responseBody);
            JsonNode candidatesNode = responseNode.path("candidates");
            
            if (candidatesNode.isEmpty()) {
                throw ModelError.emptyResponseError(ModelType.GEMINI.toString());
            }
            
            String responseText = candidatesNode.path(0).path("content").path("parts").path(0).path("text").asText();
            
            result.setResponse(responseText);
            result.setStatusCode(HttpStatus.OK.value());
            
            JsonNode usageNode = responseNode.path("usageMetadata");
            if (!usageNode.isMissingNode()) {
                int promptTokens = usageNode.path("promptTokenCount").asInt(0);
                int completionTokens = usageNode.path("candidatesTokenCount").asInt(0);
                int totalTokens = promptTokens + completionTokens;
                
                result.setInputTokens(promptTokens);
                result.setOutputTokens(completionTokens);
                result.setTotalTokens(totalTokens);
                result.setNumTokens(totalTokens); // For backward compatibility
            } else {
                tokenEstimator.estimateTokens(result, query, responseText);
            }
            
        } catch (ModelError e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error querying Gemini: {}", e.getMessage(), e);
            throw ModelError.invalidResponseError(ModelType.GEMINI.toString(), e);
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
            logger.info("Using test Gemini key, assuming service is available");
            return true;
        }
        
        try {
            String url = API_URL + "?key=" + apiKey;
            restClient.get()
                .uri(url)
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            logger.error("Error checking Gemini availability: {}", e.getMessage());
            return false;
        }
    }
}
