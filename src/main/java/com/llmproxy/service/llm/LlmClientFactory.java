package com.llmproxy.service.llm;

import com.llmproxy.exception.ModelError;
import com.llmproxy.model.ModelType;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class LlmClientFactory {
    private final Map<ModelType, LlmClient> clients;
    
    public LlmClientFactory(OpenAiClient openAiClient, GeminiClient geminiClient, 
                           MistralClient mistralClient, ClaudeClient claudeClient) {
        clients = new HashMap<>();
        clients.put(ModelType.OPENAI, openAiClient);
        clients.put(ModelType.GEMINI, geminiClient);
        clients.put(ModelType.MISTRAL, mistralClient);
        clients.put(ModelType.CLAUDE, claudeClient);
    }
    
    public LlmClient getClient(ModelType modelType) {
        LlmClient client = clients.get(modelType);
        if (client == null) {
            throw ModelError.unavailableError(modelType.toString());
        }
        return client;
    }
}
