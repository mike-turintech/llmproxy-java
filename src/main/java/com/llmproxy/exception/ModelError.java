package com.llmproxy.exception;

import lombok.Getter;

@Getter
public class ModelError extends RuntimeException {
    private final String model;
    private final int statusCode;
    private final boolean retryable;
    
    public ModelError(String model, int statusCode, String message, boolean retryable) {
        super(message);
        this.model = model;
        this.statusCode = statusCode;
        this.retryable = retryable;
    }
    
    public static ModelError unavailableError(String model) {
        return new ModelError(model, 503, "Service unavailable", true);
    }
    
    public static ModelError timeoutError(String model) {
        return new ModelError(model, 408, "Request timeout", true);
    }
    
    public static ModelError rateLimitError(String model) {
        return new ModelError(model, 429, "Rate limit exceeded", true);
    }
    
    public static ModelError apiKeyMissingError(String model) {
        return new ModelError(model, 401, "API key not configured", false);
    }
    
    public static ModelError invalidResponseError(String model, Exception e) {
        return new ModelError(model, 500, "Invalid response: " + e.getMessage(), false);
    }
    
    public static ModelError emptyResponseError(String model) {
        return new ModelError(model, 500, "Empty response", false);
    }
}
