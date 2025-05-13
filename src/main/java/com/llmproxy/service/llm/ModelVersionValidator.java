package com.llmproxy.service.llm;

import com.llmproxy.model.ModelType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Component
public class ModelVersionValidator {
    public static final String DEFAULT_OPENAI_VERSION = "gpt-4o";
    public static final String DEFAULT_GEMINI_VERSION = "gemini-1.5-pro";
    public static final String DEFAULT_MISTRAL_VERSION = "mistral-large-latest";
    public static final String DEFAULT_CLAUDE_VERSION = "claude-3-sonnet-20240229";
    
    private final Map<ModelType, List<String>> supportedModelVersions;
    private final Map<ModelType, Set<String>> validVersionSets;
    
    public ModelVersionValidator() {
        // Use EnumMap for better performance with enum keys
        supportedModelVersions = new EnumMap<>(ModelType.class);
        validVersionSets = new EnumMap<>(ModelType.class);
        
        // Define lists once and store immutable references
        List<String> openaiVersions = List.of(
                "gpt-4o",
                "gpt-4o-mini",
                "gpt-4-turbo",
                "gpt-4",
                "gpt-4-vision-preview",
                "gpt-3.5-turbo",
                "gpt-3.5-turbo-16k"
        );
        
        List<String> geminiVersions = List.of(
                "gemini-2.5-flash-preview-04-17",
                "gemini-2.5-pro-preview-03-25",
                "gemini-2.0-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash",
                "gemini-1.5-flash-8b",
                "gemini-1.5-pro",
                "gemini-pro",
                "gemini-pro-vision"
        );
        
        List<String> mistralVersions = List.of(
                "codestral-latest",
                "mistral-large-latest",
                "mistral-saba-latest",
                "mistral-tiny",
                "mistral-small",
                "mistral-medium",
                "mistral-large"
        );
        
        List<String> claudeVersions = List.of(
                "claude-3-opus-20240229",
                "claude-3-sonnet-20240229",
                "claude-3-haiku-20240307",
                "claude-3-opus",
                "claude-3-sonnet",
                "claude-3-haiku",
                "claude-2.1",
                "claude-2.0"
        );
        
        supportedModelVersions.put(ModelType.OPENAI, openaiVersions);
        supportedModelVersions.put(ModelType.GEMINI, geminiVersions);
        supportedModelVersions.put(ModelType.MISTRAL, mistralVersions);
        supportedModelVersions.put(ModelType.CLAUDE, claudeVersions);
        
        // Create sets for O(1) lookups instead of stream filtering
        for (Map.Entry<ModelType, List<String>> entry : supportedModelVersions.entrySet()) {
            validVersionSets.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
    }
    
    public String validateModelVersion(ModelType modelType, String version) {
        if (version == null || version.isBlank()) {
            return getDefaultVersionForModel(modelType);
        }
        
        Set<String> validVersions = validVersionSets.get(modelType);
        return validVersions != null && validVersions.contains(version) ? 
               version : getDefaultVersionForModel(modelType);
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
        return supportedModelVersions.getOrDefault(modelType, Collections.emptyList());
    }
}