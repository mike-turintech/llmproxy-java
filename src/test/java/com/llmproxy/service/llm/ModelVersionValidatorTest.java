package com.llmproxy.service.llm;

import com.llmproxy.model.ModelType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ModelVersionValidatorTest {

    private final ModelVersionValidator validator = new ModelVersionValidator();

    @Test
    void validateModelVersion_nullVersion_returnsDefaultVersion() {
        assertEquals(ModelVersionValidator.DEFAULT_OPENAI_VERSION, 
                validator.validateModelVersion(ModelType.OPENAI, null));
        assertEquals(ModelVersionValidator.DEFAULT_GEMINI_VERSION, 
                validator.validateModelVersion(ModelType.GEMINI, null));
        assertEquals(ModelVersionValidator.DEFAULT_MISTRAL_VERSION, 
                validator.validateModelVersion(ModelType.MISTRAL, null));
        assertEquals(ModelVersionValidator.DEFAULT_CLAUDE_VERSION, 
                validator.validateModelVersion(ModelType.CLAUDE, null));
    }

    @Test
    void validateModelVersion_emptyVersion_returnsDefaultVersion() {
        assertEquals(ModelVersionValidator.DEFAULT_OPENAI_VERSION, 
                validator.validateModelVersion(ModelType.OPENAI, ""));
        assertEquals(ModelVersionValidator.DEFAULT_GEMINI_VERSION, 
                validator.validateModelVersion(ModelType.GEMINI, ""));
        assertEquals(ModelVersionValidator.DEFAULT_MISTRAL_VERSION, 
                validator.validateModelVersion(ModelType.MISTRAL, ""));
        assertEquals(ModelVersionValidator.DEFAULT_CLAUDE_VERSION, 
                validator.validateModelVersion(ModelType.CLAUDE, ""));
    }

    @ParameterizedTest
    @MethodSource("provideValidModelVersions")
    void validateModelVersion_validVersion_returnsVersion(ModelType modelType, String version) {
        assertEquals(version, validator.validateModelVersion(modelType, version));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidModelVersions")
    void validateModelVersion_invalidVersion_returnsDefaultVersion(ModelType modelType, String version) {
        String defaultVersion = switch (modelType) {
            case OPENAI -> ModelVersionValidator.DEFAULT_OPENAI_VERSION;
            case GEMINI -> ModelVersionValidator.DEFAULT_GEMINI_VERSION;
            case MISTRAL -> ModelVersionValidator.DEFAULT_MISTRAL_VERSION;
            case CLAUDE -> ModelVersionValidator.DEFAULT_CLAUDE_VERSION;
        };
        
        assertEquals(defaultVersion, validator.validateModelVersion(modelType, version));
    }

    @Test
    void getSupportedVersionsForModel_returnsCorrectVersions() {
        assertNotNull(validator.getSupportedVersionsForModel(ModelType.OPENAI));
        assertNotNull(validator.getSupportedVersionsForModel(ModelType.GEMINI));
        assertNotNull(validator.getSupportedVersionsForModel(ModelType.MISTRAL));
        assertNotNull(validator.getSupportedVersionsForModel(ModelType.CLAUDE));
        
        assertTrue(validator.getSupportedVersionsForModel(ModelType.OPENAI).contains("gpt-3.5-turbo"));
        assertTrue(validator.getSupportedVersionsForModel(ModelType.GEMINI).contains("gemini-pro"));
        assertTrue(validator.getSupportedVersionsForModel(ModelType.MISTRAL).contains("mistral-medium"));
        assertTrue(validator.getSupportedVersionsForModel(ModelType.CLAUDE).contains("claude-3-sonnet"));
    }

    private static Stream<Arguments> provideValidModelVersions() {
        return Stream.of(
                Arguments.of(ModelType.OPENAI, "gpt-3.5-turbo"),
                Arguments.of(ModelType.OPENAI, "gpt-4"),
                Arguments.of(ModelType.GEMINI, "gemini-pro"),
                Arguments.of(ModelType.MISTRAL, "mistral-medium"),
                Arguments.of(ModelType.CLAUDE, "claude-3-sonnet")
        );
    }

    private static Stream<Arguments> provideInvalidModelVersions() {
        return Stream.of(
                Arguments.of(ModelType.OPENAI, "invalid-model"),
                Arguments.of(ModelType.GEMINI, "gpt-4"),
                Arguments.of(ModelType.MISTRAL, "claude-3"),
                Arguments.of(ModelType.CLAUDE, "gemini-pro")
        );
    }
}
