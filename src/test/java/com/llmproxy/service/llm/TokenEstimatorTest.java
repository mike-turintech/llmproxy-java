package com.llmproxy.service.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TokenEstimatorTest {

    private final TokenEstimator tokenEstimator = new TokenEstimator();

    @Test
    void estimateTokenCount_nullText_returnsZero() {
        assertEquals(0, tokenEstimator.estimateTokenCount(null));
    }

    @Test
    void estimateTokenCount_emptyText_returnsZero() {
        assertEquals(0, tokenEstimator.estimateTokenCount(""));
    }

    @ParameterizedTest
    @MethodSource("provideTextsForTokenEstimation")
    void estimateTokenCount_validText_returnsEstimatedTokens(String text, int expectedTokens) {
        assertEquals(expectedTokens, tokenEstimator.estimateTokenCount(text));
    }

    @Test
    void estimateTokens_updatesQueryResultWithTokenCounts() {
        QueryResult result = QueryResult.builder().build();
        String query = "This is a test query";
        String response = "This is a test response";

        tokenEstimator.estimateTokens(result, query, response);

        assertEquals(tokenEstimator.estimateTokenCount(query), result.getInputTokens());
        assertEquals(tokenEstimator.estimateTokenCount(response), result.getOutputTokens());
        assertEquals(result.getInputTokens() + result.getOutputTokens(), result.getTotalTokens());
        assertEquals(result.getTotalTokens(), result.getNumTokens());
    }

    @Test
    void estimateTokens_doesNotUpdateIfTotalTokensAlreadySet() {
        QueryResult result = QueryResult.builder()
                .inputTokens(10)
                .outputTokens(20)
                .totalTokens(30)
                .numTokens(30)
                .build();
        String query = "This is a test query";
        String response = "This is a test response";

        tokenEstimator.estimateTokens(result, query, response);

        assertEquals(10, result.getInputTokens());
        assertEquals(20, result.getOutputTokens());
        assertEquals(30, result.getTotalTokens());
        assertEquals(30, result.getNumTokens());
    }

    private static Stream<Arguments> provideTextsForTokenEstimation() {
        return Stream.of(
                Arguments.of("Hello world", 2),
                Arguments.of("This is a longer text that should have more tokens", 12),
                Arguments.of("A", 0),
                Arguments.of("AB", 0),
                Arguments.of("ABCD", 1)
        );
    }
}
