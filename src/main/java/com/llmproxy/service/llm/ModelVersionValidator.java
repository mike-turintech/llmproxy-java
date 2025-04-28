package com.llmproxy.service.llm;

import com.llmproxy.model.ModelType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ModelVersionValidator {
    public static final String DEFAULT_OPENAI_VERSION = "gpt-3.5-turbo";
    public static final String DEFAULT_GEMINI_VERSION = "gemini-pro";
    public static final String DEFAULT_MISTRAL_VERSION = "mistral-medium";
    public static final String DEFAULT_CLAUDE_VERSION = "claude-3-sonnet-20240229";
    
    private final Map<ModelType, List<String>> supportedModelVersions;
    
    public ModelVersionValidator() {
        supportedModelVersions = new HashMap<>();
        supportedModelVersions.put(ModelType.OPENAI, List.of(
                "gpt-3.5-turbo",
                "gpt-4",
                "gpt-4-turbo"
        ));
        supportedModelVersions.put(ModelType.GEMINI, List.of(
                "gemini-pro",
                "gemini-pro-vision"
        ));
        supportedModelVersions.put(ModelType.MISTRAL, List.of(
                "mistral-tiny",
                "mistral-small",
                "mistral-medium",
                "mistral-large"
        ));
        supportedModelVersions.put(ModelType.CLAUDE, List.of(
                "claude-3-haiku",
                "claude-3-sonnet",
                "claude-3-opus"
        ));
    }
    
    public String validateModelVersion(ModelType modelType, String version) {
        if (version == null || version.isBlank()) {
            return getDefaultVersionForModel(modelType);
        }
        
        List<String> validVersions = supportedModelVersions.get(modelType);
        return validVersions.stream()
                .filter(v -> v.equals(version))
                .findFirst()
                .orElse(getDefaultVersionForModel(modelType));
    }
    
    private String getDefaultVersionForModel(ModelType modelType) {
        return switch (modelType) {
            case OPENAI -> DEFAULT_OPENAI_VERSION;
            case GEMINI -> DEFAULT_GEMINI_VERSION;
            case MISTRAL -> DEFAULT_MISTRAL_VERSION;
            case CLAUDE -> DEFAULT_CLAUDE_VERSION;
        };
    }
    
    public List<String> getSupportedVersionsForModel(ModelType modelType) {
        return supportedModelVersions.getOrDefault(modelType, List.of());
    }
}
