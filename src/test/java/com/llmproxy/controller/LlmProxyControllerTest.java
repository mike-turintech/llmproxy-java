package com.llmproxy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmproxy.exception.ModelError;
import com.llmproxy.model.ModelType;
import com.llmproxy.model.QueryRequest;
import com.llmproxy.model.QueryResponse;
import com.llmproxy.model.StatusResponse;
import com.llmproxy.service.cache.CacheService;
import com.llmproxy.service.llm.LlmClient;
import com.llmproxy.service.llm.LlmClientFactory;
import com.llmproxy.service.llm.QueryResult;
import com.llmproxy.service.ratelimit.RateLimiterService;
import com.llmproxy.service.router.RouterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LlmProxyController.class)
class LlmProxyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RouterService routerService;

    @MockBean
    private LlmClientFactory clientFactory;

    @MockBean
    private CacheService cacheService;

    @MockBean
    private RateLimiterService rateLimiterService;

    @MockBean
    private LlmClient llmClient;

    @Test
    void query_validRequest_returnsResponse() throws Exception {
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .build();
        
        QueryResult queryResult = QueryResult.builder()
                .response("Test response")
                .statusCode(HttpStatus.OK.value())
                .inputTokens(10)
                .outputTokens(20)
                .totalTokens(30)
                .numTokens(30)
                .responseTimeMs(100)
                .build();
        
        when(rateLimiterService.allowClient(anyString())).thenReturn(true);
        when(cacheService.get(any(QueryRequest.class))).thenReturn(null);
        when(routerService.routeRequest(any(QueryRequest.class))).thenReturn(ModelType.OPENAI);
        when(clientFactory.getClient(ModelType.OPENAI)).thenReturn(llmClient);
        when(llmClient.query(anyString(), anyString())).thenReturn(queryResult);
        
        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Test response"))
                .andExpect(jsonPath("$.model").value("OPENAI"))
                .andExpect(jsonPath("$.inputTokens").value(10))
                .andExpect(jsonPath("$.outputTokens").value(20))
                .andExpect(jsonPath("$.totalTokens").value(30));
    }

    @Test
    void query_emptyQuery_returnsBadRequest() throws Exception {
        QueryRequest request = QueryRequest.builder()
                .query("")
                .build();
        
        when(rateLimiterService.allowClient(anyString())).thenReturn(true);
        
        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Query cannot be empty"))
                .andExpect(jsonPath("$.errorType").value("validation_error"));
    }

    @Test
    void query_rateLimited_returnsTooManyRequests() throws Exception {
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .build();
        
        when(rateLimiterService.allowClient(anyString())).thenReturn(false);
        
        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded. Please try again later."))
                .andExpect(jsonPath("$.errorType").value("rate_limit"));
    }

    @Test
    void query_cachedResponse_returnsCachedResponse() throws Exception {
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .build();
        
        QueryResponse cachedResponse = QueryResponse.builder()
                .response("Cached response")
                .model(ModelType.OPENAI)
                .cached(true)
                .timestamp(Instant.now())
                .build();
        
        when(rateLimiterService.allowClient(anyString())).thenReturn(true);
        when(cacheService.get(any(QueryRequest.class))).thenReturn(cachedResponse);
        
        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Cached response"))
                .andExpect(jsonPath("$.model").value("OPENAI"))
                .andExpect(jsonPath("$.cached").value(true));
    }

    @Test
    void query_modelError_returnsErrorResponse() throws Exception {
        QueryRequest request = QueryRequest.builder()
                .query("Test query")
                .build();
        
        when(rateLimiterService.allowClient(anyString())).thenReturn(true);
        when(cacheService.get(any(QueryRequest.class))).thenReturn(null);
        when(routerService.routeRequest(any(QueryRequest.class))).thenReturn(ModelType.OPENAI);
        when(clientFactory.getClient(ModelType.OPENAI)).thenReturn(llmClient);
        when(llmClient.query(anyString(), anyString())).thenThrow(ModelError.apiKeyMissingError(ModelType.OPENAI.toString()));
        when(routerService.fallbackOnError(any(), any(), any())).thenThrow(ModelError.unavailableError("all"));
        
        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service unavailable"))
                .andExpect(jsonPath("$.errorType").value("ModelError"));
    }

    @Test
    void status_returnsAvailability() throws Exception {
        StatusResponse statusResponse = StatusResponse.builder()
                .openai(true)
                .gemini(false)
                .mistral(true)
                .claude(false)
                .build();
        
        when(rateLimiterService.allowClient(anyString())).thenReturn(true);
        when(routerService.getAvailability()).thenReturn(statusResponse);
        
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openai").value(true))
                .andExpect(jsonPath("$.gemini").value(false))
                .andExpect(jsonPath("$.mistral").value(true))
                .andExpect(jsonPath("$.claude").value(false));
    }

    @Test
    void health_returnsOk() throws Exception {
        when(rateLimiterService.allowClient(anyString())).thenReturn(true);
        
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void download_validRequest_returnsFile() throws Exception {
        when(rateLimiterService.allowClient(anyString())).thenReturn(true);
        
        mockMvc.perform(post("/api/download")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"response\":\"Test response\",\"format\":\"txt\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    byte[] content = result.getResponse().getContentAsByteArray();
                    String responseText = new String(content);
                    assert responseText.equals("Test response");
                });
    }
}
