package com.llmproxy.service.router;

import com.llmproxy.exception.ModelError;
import com.llmproxy.model.ModelType;
import com.llmproxy.model.QueryRequest;
import com.llmproxy.model.StatusResponse;
import com.llmproxy.model.TaskType;
import com.llmproxy.service.llm.LlmClient;
import com.llmproxy.service.llm.LlmClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouterServiceTest {

    @Mock
    private LlmClientFactory clientFactory;
    
    @Mock
    private LlmClient openAiClient;
    
    @Mock
    private LlmClient geminiClient;
    
    @Mock
    private LlmClient mistralClient;
    
    @Mock
    private LlmClient claudeClient;
    
    private RouterService routerService;

    @BeforeEach
    void setUp() {
        routerService = new RouterService(clientFactory);
        routerService.setTestMode(true); // Avoid actual availability checks
        
        lenient().when(openAiClient.getModelType()).thenReturn(ModelType.OPENAI);
        lenient().when(geminiClient.getModelType()).thenReturn(ModelType.GEMINI);
        lenient().when(mistralClient.getModelType()).thenReturn(ModelType.MISTRAL);
        lenient().when(claudeClient.getModelType()).thenReturn(ModelType.CLAUDE);
        
        lenient().when(clientFactory.getClient(ModelType.OPENAI)).thenReturn(openAiClient);
        lenient().when(clientFactory.getClient(ModelType.GEMINI)).thenReturn(geminiClient);
        lenient().when(clientFactory.getClient(ModelType.MISTRAL)).thenReturn(mistralClient);
        lenient().when(clientFactory.getClient(ModelType.CLAUDE)).thenReturn(claudeClient);
    }

    @Test
    void getAvailability_returnsCorrectStatus() {
        routerService.setModelAvailability(ModelType.OPENAI, true);
        routerService.setModelAvailability(ModelType.GEMINI, false);
        routerService.setModelAvailability(ModelType.MISTRAL, true);
        routerService.setModelAvailability(ModelType.CLAUDE, false);
        
        StatusResponse status = routerService.getAvailability();
        
        assertTrue(status.isOpenai());
        assertFalse(status.isGemini());
        assertTrue(status.isMistral());
        assertFalse(status.isClaude());
    }

    @Test
    void routeRequest_withSpecifiedAvailableModel_usesSpecifiedModel() {
        routerService.setModelAvailability(ModelType.OPENAI, true);
        routerService.setModelAvailability(ModelType.GEMINI, true);
        
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .model(ModelType.GEMINI)
                .build();
        
        ModelType result = routerService.routeRequest(request);
        
        assertEquals(ModelType.GEMINI, result);
    }

    @Test
    void routeRequest_withSpecifiedUnavailableModel_usesAlternative() {
        routerService.setModelAvailability(ModelType.OPENAI, true);
        routerService.setModelAvailability(ModelType.GEMINI, false);
        routerService.setModelAvailability(ModelType.MISTRAL, false);
        routerService.setModelAvailability(ModelType.CLAUDE, false);
        
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .model(ModelType.GEMINI)
                .build();
        
        ModelType result = routerService.routeRequest(request);
        
        assertEquals(ModelType.OPENAI, result);
    }

    @Test
    void routeRequest_withTaskType_usesAppropriateModel() {
        routerService.setModelAvailability(ModelType.OPENAI, true);
        routerService.setModelAvailability(ModelType.GEMINI, true);
        routerService.setModelAvailability(ModelType.MISTRAL, true);
        routerService.setModelAvailability(ModelType.CLAUDE, true);
        
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .taskType(TaskType.SUMMARIZATION)
                .build();
        
        ModelType result = routerService.routeRequest(request);
        
        assertEquals(ModelType.CLAUDE, result);
    }

    @Test
    void routeRequest_withTaskTypeButUnavailableModel_usesAlternative() {
        routerService.setModelAvailability(ModelType.OPENAI, true);
        routerService.setModelAvailability(ModelType.GEMINI, false);
        routerService.setModelAvailability(ModelType.MISTRAL, false);
        routerService.setModelAvailability(ModelType.CLAUDE, false);
        
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .taskType(TaskType.SENTIMENT_ANALYSIS)
                .build();
        
        ModelType result = routerService.routeRequest(request);
        
        assertEquals(ModelType.OPENAI, result);
    }

    @Test
    void routeRequest_noModelOrTaskType_usesRandomAvailableModel() {
        routerService.setModelAvailability(ModelType.OPENAI, true);
        routerService.setModelAvailability(ModelType.GEMINI, false);
        routerService.setModelAvailability(ModelType.MISTRAL, false);
        routerService.setModelAvailability(ModelType.CLAUDE, false);
        
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .build();
        
        ModelType result = routerService.routeRequest(request);
        
        assertEquals(ModelType.OPENAI, result);
    }

    @Test
    void routeRequest_noAvailableModels_throwsException() {
        routerService.setModelAvailability(ModelType.OPENAI, false);
        routerService.setModelAvailability(ModelType.GEMINI, false);
        routerService.setModelAvailability(ModelType.MISTRAL, false);
        routerService.setModelAvailability(ModelType.CLAUDE, false);
        
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .build();
        
        assertThrows(ModelError.class, () -> routerService.routeRequest(request));
    }

    @Test
    void fallbackOnError_withRetryableError_returnsAlternativeModel() {
        routerService.setModelAvailability(ModelType.OPENAI, false);
        routerService.setModelAvailability(ModelType.GEMINI, true);
        routerService.setModelAvailability(ModelType.MISTRAL, false);
        routerService.setModelAvailability(ModelType.CLAUDE, false);
        
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .build();
        
        ModelError error = ModelError.rateLimitError(ModelType.OPENAI.toString());
        
        ModelType result = routerService.fallbackOnError(ModelType.OPENAI, request, error);
        
        assertEquals(ModelType.GEMINI, result);
    }

    @Test
    void fallbackOnError_withNonRetryableError_throwsException() {
        routerService.setModelAvailability(ModelType.OPENAI, false);
        routerService.setModelAvailability(ModelType.GEMINI, true);
        
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .build();
        
        ModelError error = ModelError.apiKeyMissingError(ModelType.OPENAI.toString());
        
        assertThrows(ModelError.class, () -> routerService.fallbackOnError(ModelType.OPENAI, request, error));
    }

    @Test
    void fallbackOnError_noAvailableAlternatives_throwsException() {
        routerService.setModelAvailability(ModelType.OPENAI, false);
        routerService.setModelAvailability(ModelType.GEMINI, false);
        routerService.setModelAvailability(ModelType.MISTRAL, false);
        routerService.setModelAvailability(ModelType.CLAUDE, false);
        
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .build();
        
        ModelError error = ModelError.rateLimitError(ModelType.OPENAI.toString());
        
        assertThrows(ModelError.class, () -> routerService.fallbackOnError(ModelType.OPENAI, request, error));
    }

    @Test
    void fallbackOnError_withUserSpecifiedModel_usesThatModel() {
        routerService.setModelAvailability(ModelType.OPENAI, false);
        routerService.setModelAvailability(ModelType.GEMINI, true);
        routerService.setModelAvailability(ModelType.MISTRAL, true);
        
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .model(ModelType.MISTRAL)
                .build();
        
        ModelError error = ModelError.rateLimitError(ModelType.OPENAI.toString());
        
        ModelType result = routerService.fallbackOnError(ModelType.OPENAI, request, error);
        
        assertEquals(ModelType.MISTRAL, result);
    }

    // ---- Enhanced Robustness tests for fallbackOnError start here ----

    @Test
    void fallbackOnError_withUserSpecifiedFallbackButUnavailable_selectsOtherAvailable() {
        routerService.setModelAvailability(ModelType.OPENAI, false);
        routerService.setModelAvailability(ModelType.GEMINI, false); // user fallback is unavailable
        routerService.setModelAvailability(ModelType.MISTRAL, true); // Mistral is available
        routerService.setModelAvailability(ModelType.CLAUDE, false);
        
        // User wanted GEMINI, which is unavailable
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .model(ModelType.GEMINI)
                .build();
        
        ModelError error = ModelError.rateLimitError(ModelType.OPENAI.toString());

        // Should select the next available model, which is MISTRAL
        ModelType result = routerService.fallbackOnError(ModelType.OPENAI, request, error);
        assertEquals(ModelType.MISTRAL, result);
    }

    @Test
    void fallbackOnError_withAllButOneModelAvailable_returnsThatModel() {
        // Only Claude is up
        routerService.setModelAvailability(ModelType.OPENAI, false);
        routerService.setModelAvailability(ModelType.GEMINI, false);
        routerService.setModelAvailability(ModelType.MISTRAL, false);
        routerService.setModelAvailability(ModelType.CLAUDE, true);

        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .build();
        ModelError error = ModelError.rateLimitError(ModelType.OPENAI.toString());
        ModelType result = routerService.fallbackOnError(ModelType.OPENAI, request, error);
        assertEquals(ModelType.CLAUDE, result);
    }

    @Test
    void fallbackOnError_initialModelIsOnlyAvailableOptionButFails_noAlternatives_throw() {
        // Only OpenAI is up, but it fails and fallback should not find any others
        routerService.setModelAvailability(ModelType.OPENAI, true);
        routerService.setModelAvailability(ModelType.GEMINI, false);
        routerService.setModelAvailability(ModelType.MISTRAL, false);
        routerService.setModelAvailability(ModelType.CLAUDE, false);
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .build();
        ModelError error = ModelError.rateLimitError(ModelType.OPENAI.toString());
        // Mark OpenAI as unavailable now (simulate it failed and shouldn't be retried)
        routerService.setModelAvailability(ModelType.OPENAI, false);
        assertThrows(ModelError.class, () -> routerService.fallbackOnError(ModelType.OPENAI, request, error));
    }

    @Test
    void fallbackOnError_withTaskTypeAndPreferredModelUnavailable_selectsTaskTypeAlternative() {
        // Use sentiment_analysis mapping: prefer OpenAI, if not, next available
        routerService.setModelAvailability(ModelType.OPENAI, false);
        routerService.setModelAvailability(ModelType.GEMINI, false);
        routerService.setModelAvailability(ModelType.MISTRAL, true);
        routerService.setModelAvailability(ModelType.CLAUDE, false);

        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .taskType(TaskType.SENTIMENT_ANALYSIS)
                .build();
        ModelError error = ModelError.rateLimitError(ModelType.OPENAI.toString());
        ModelType result = routerService.fallbackOnError(ModelType.OPENAI, request, error);
        assertEquals(ModelType.MISTRAL, result);
    }

    @Test
    void fallbackOnError_specifiedModelEqualsFailedModel_returnsAlternative() {
        // User asked for MISTRAL, but MISTRAL failed, so must route elsewhere
        routerService.setModelAvailability(ModelType.OPENAI, false);
        routerService.setModelAvailability(ModelType.GEMINI, true);
        routerService.setModelAvailability(ModelType.MISTRAL, true); // was up, but failed
        routerService.setModelAvailability(ModelType.CLAUDE, true);

        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .model(ModelType.MISTRAL)
                .build();
        // Mark MISTRAL as down now ("fails")
        routerService.setModelAvailability(ModelType.MISTRAL, false);

        ModelError error = ModelError.rateLimitError(ModelType.MISTRAL.toString());
        // Should skip MISTRAL and pick next available: CLAUDE or GEMINI (impl can pick one of them; assert one of them)
        ModelType result = routerService.fallbackOnError(ModelType.MISTRAL, request, error);
        assertTrue(result == ModelType.GEMINI || result == ModelType.CLAUDE);
    }
}