package com.llmproxy.service.llm;

import org.springframework.stereotype.Component;

@Component
public class TokenEstimator {
    /**
     * Estimates the number of tokens in the given text using a simple length-based heuristic.
     * 
     * @param text the text to analyze
     * @return the estimated token count (text length divided by 4)
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        return text.length() / 4;
    }
    
    /**
     * Updates the QueryResult with token count estimates if not already set.
     * 
     * @param result the query result to update
     * @param query the input query text
     * @param response the response text
     */
    public void estimateTokens(QueryResult result, String query, String response) {
        if (result.getTotalTokens() == 0) {
            int inputTokens = estimateTokenCount(query);
            int outputTokens = estimateTokenCount(response);
            int totalTokens = inputTokens + outputTokens;
            
            result.setInputTokens(inputTokens);
            result.setOutputTokens(outputTokens);
            result.setTotalTokens(totalTokens);
            result.setNumTokens(totalTokens); // For backward compatibility
        }
    }
}