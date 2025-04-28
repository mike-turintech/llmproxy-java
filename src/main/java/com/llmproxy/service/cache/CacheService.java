package com.llmproxy.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.llmproxy.model.QueryRequest;
import com.llmproxy.model.QueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {
    private final Cache<String, QueryResponse> cache;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    
    public CacheService(
            @Value("${cache.enabled:true}") boolean enabled,
            @Value("${cache.ttl.seconds:300}") int ttlSeconds,
            @Value("${cache.max-items:1000}") int maxItems,
            ObjectMapper objectMapper) {
        
        this.enabled = enabled;
        this.objectMapper = objectMapper;
        
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxItems)
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .build();
        
        log.info("Cache initialized: enabled={}, ttl={}s, maxItems={}", enabled, ttlSeconds, maxItems);
    }
    
    public QueryResponse get(QueryRequest request) {
        if (!enabled) {
            return null;
        }
        
        String cacheKey = generateCacheKey(request);
        QueryResponse cachedResponse = cache.getIfPresent(cacheKey);
        
        if (cachedResponse != null) {
            log.debug("Cache hit for key: {}", cacheKey);
            return cachedResponse;
        }
        
        log.debug("Cache miss for key: {}", cacheKey);
        return null;
    }
    
    public void set(QueryRequest request, QueryResponse response) {
        if (!enabled) {
            return;
        }
        
        String cacheKey = generateCacheKey(request);
        cache.put(cacheKey, response);
        
        log.debug("Added response to cache with key: {}, model: {}", cacheKey, response.getModel());
    }
    
    private String generateCacheKey(QueryRequest request) {
        Map<String, String> data = new HashMap<>();
        data.put("query", request.getQuery());
        data.put("model", request.getModel() != null ? request.getModel().toString() : "");
        data.put("task_type", request.getTaskType() != null ? request.getTaskType().toString() : "");
        
        try {
            String jsonData = objectMapper.writeValueAsString(data);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jsonData.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            log.error("Error generating cache key: {}", e.getMessage());
            return String.format("%s:%s:%s", 
                request.getQuery(), 
                request.getModel(), 
                request.getTaskType());
        }
    }
}
