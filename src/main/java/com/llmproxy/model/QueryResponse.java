package com.llmproxy.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse {
    private String response;
    private ModelType model;
    private long responseTimeMs;
    private Instant timestamp;
    private boolean cached;
    private String error;
    private String errorType;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
    private int numTokens; // Deprecated: Use totalTokens instead
    private int numRetries;
    private String requestId;
    private ModelType originalModel; // If fallback occurred
}
