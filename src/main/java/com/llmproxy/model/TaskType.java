package com.llmproxy.model;

public enum TaskType {
    TEXT_GENERATION("text_generation"),
    SUMMARIZATION("summarization"),
    SENTIMENT_ANALYSIS("sentiment_analysis"),
    QUESTION_ANSWERING("question_answering"),
    OTHER("other");
    
    private final String value;
    
    TaskType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static TaskType fromString(String text) {
        for (TaskType type : TaskType.values()) {
            if (type.value.equalsIgnoreCase(text)) {
                return type;
            }
        }
        return OTHER;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
