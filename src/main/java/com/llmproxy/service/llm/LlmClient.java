package com.llmproxy.service.llm;

import com.llmproxy.model.ModelType;

public interface LlmClient {
    QueryResult query(String query, String modelVersion);
    boolean checkAvailability();
    ModelType getModelType();
}
