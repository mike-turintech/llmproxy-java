package com.llmproxy.service.llm;

import org.springframework.stereotype.Component;

@Component
public class TokenEstimator {
    public int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        return text.length() / 4;
    }
    
    public void estimateTokens(QueryResult result, String query, String response) {
        if (result.getTotalTokens() == 0) {
            result.setInputTokens(estimateTokenCount(query));
            result.setOutputTokens(estimateTokenCount(response));
            result.setTotalTokens(result.getInputTokens() + result.getOutputTokens());
            result.setNumTokens(result.getTotalTokens()); // For backward compatibility
        }
    }
}
