package com.llmproxy.model;

import java.util.Map;
import java.util.HashMap;

public enum TaskType {
    TEXT_GENERATION("text_generation"),
    SUMMARIZATION("summarization"),
    SENTIMENT_ANALYSIS("sentiment_analysis"),
    QUESTION_ANSWERING("question_answering"),
    OTHER("other");
    
    private final String value;
    
    // Cache for fromString method to improve performance
    private static final Map<String, TaskType> STRING_TO_ENUM_MAP = new HashMap<>();

    static {
        for (TaskType type : values()) {
            STRING_TO_ENUM_MAP.put(type.value.toLowerCase(), type);
        }
    }
    
    TaskType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static TaskType fromString(String text) {
        if (text == null) {
            return OTHER; // Or throw IllegalArgumentException, depending on desired behavior
        }
        TaskType type = STRING_TO_ENUM_MAP.get(text.toLowerCase());
        return type != null ? type : OTHER;
    }
    
    @Override
    public String toString() {
        return value;
    }
}