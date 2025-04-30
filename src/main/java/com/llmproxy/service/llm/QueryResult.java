package com.llmproxy.service.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResult {
    private String response;
    private long responseTimeMs;
    private int statusCode;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
    private int numTokens; // Deprecated: Use totalTokens instead
    private int numRetries;
}
