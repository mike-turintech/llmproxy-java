package com.llmproxy.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {
    private String query;
    private ModelType model; // Optional - if not provided, will be determined by the proxy
    private String modelVersion; // Optional - specific version of the model to use
    private TaskType taskType; // Optional - helps with model selection
    private String requestId; // Optional - for tracking requests
}
