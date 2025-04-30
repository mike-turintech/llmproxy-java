package com.llmproxy.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmproxy.model.ModelType;
import com.llmproxy.model.QueryRequest;
import com.llmproxy.model.QueryResponse;
import com.llmproxy.model.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheServiceTest {

    private CacheService cacheService;
    private QueryRequest request;
    private QueryResponse response;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService(true, 300, 1000, new ObjectMapper());
        
        request = QueryRequest.builder()
                .query("Test query")
                .model(ModelType.OPENAI)
                .taskType(TaskType.TEXT_GENERATION)
                .build();
        
        response = QueryResponse.builder()
                .response("Test response")
                .model(ModelType.OPENAI)
                .build();
    }

    @Test
    void get_cacheDisabled_returnsNull() {
        CacheService disabledCache = new CacheService(false, 300, 1000, new ObjectMapper());
        disabledCache.set(request, response);
        
        assertNull(disabledCache.get(request));
    }

    @Test
    void get_cacheEnabled_cacheMiss_returnsNull() {
        assertNull(cacheService.get(request));
    }

    @Test
    void get_cacheEnabled_cacheHit_returnsResponse() {
        cacheService.set(request, response);
        
        QueryResponse cachedResponse = cacheService.get(request);
        assertNotNull(cachedResponse);
        assertEquals(response.getResponse(), cachedResponse.getResponse());
        assertEquals(response.getModel(), cachedResponse.getModel());
    }

    @Test
    void set_cacheDisabled_doesNothing() {
        CacheService disabledCache = new CacheService(false, 300, 1000, new ObjectMapper());
        disabledCache.set(request, response);
        
        assertNull(disabledCache.get(request));
    }

    @Test
    void set_cacheEnabled_storesResponse() {
        cacheService.set(request, response);
        
        QueryResponse cachedResponse = cacheService.get(request);
        assertNotNull(cachedResponse);
        assertEquals(response.getResponse(), cachedResponse.getResponse());
    }

    @Test
    void generateCacheKey_differentQueries_differentKeys() {
        QueryRequest request1 = QueryRequest.builder()
                .query("Query 1")
                .model(ModelType.OPENAI)
                .build();
        
        QueryRequest request2 = QueryRequest.builder()
                .query("Query 2")
                .model(ModelType.OPENAI)
                .build();
        
        cacheService.set(request1, response);
        
        assertNotNull(cacheService.get(request1));
        assertNull(cacheService.get(request2));
    }

    @Test
    void generateCacheKey_differentModels_differentKeys() {
        QueryRequest request1 = QueryRequest.builder()
                .query("Test query")
                .model(ModelType.OPENAI)
                .build();
        
        QueryRequest request2 = QueryRequest.builder()
                .query("Test query")
                .model(ModelType.GEMINI)
                .build();
        
        cacheService.set(request1, response);
        
        assertNotNull(cacheService.get(request1));
        assertNull(cacheService.get(request2));
    }

    @Test
    void generateCacheKey_differentTaskTypes_differentKeys() {
        QueryRequest request1 = QueryRequest.builder()
                .query("Test query")
                .taskType(TaskType.TEXT_GENERATION)
                .build();
        
        QueryRequest request2 = QueryRequest.builder()
                .query("Test query")
                .taskType(TaskType.SUMMARIZATION)
                .build();
        
        cacheService.set(request1, response);
        
        assertNotNull(cacheService.get(request1));
        assertNull(cacheService.get(request2));
    }
}
