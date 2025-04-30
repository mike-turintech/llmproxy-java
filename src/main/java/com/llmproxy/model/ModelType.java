package com.llmproxy.model;

public enum ModelType {
    OPENAI("openai"),
    GEMINI("gemini"),
    MISTRAL("mistral"),
    CLAUDE("claude");
    
    private final String value;
    
    ModelType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static ModelType fromString(String text) {
        for (ModelType type : ModelType.values()) {
            if (type.value.equalsIgnoreCase(text)) {
                return type;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
