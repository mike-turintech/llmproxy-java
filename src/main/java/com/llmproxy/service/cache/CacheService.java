package com.llmproxy.service.cache;

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
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {
    private final Cache<String, QueryResponse> cache;
    private final boolean enabled;

    public CacheService(
            @Value("${cache.enabled:true}") boolean enabled,
            @Value("${cache.ttl.seconds:300}") int ttlSeconds,
            @Value("${cache.max-items:1000}") int maxItems,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper // Kept for bean compatibility, but not used
    ) {
        this.enabled = enabled;

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
        // Efficiently build the key string
        String query = request.getQuery() != null ? request.getQuery() : "";
        String model = request.getModel() != null ? request.getModel().toString() : "";
        String taskType = request.getTaskType() != null ? request.getTaskType().toString() : "";

        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append("query:").append(query).append(';');
        keyBuilder.append("model:").append(model).append(';');
        keyBuilder.append("task_type:").append(taskType);

        String rawKey = keyBuilder.toString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            // Efficient hex conversion
            char[] hexArray = "0123456789abcdef".toCharArray();
            char[] hexChars = new char[hash.length * 2];
            for (int j = 0; j < hash.length; j++) {
                int v = hash[j] & 0xFF;
                hexChars[j * 2] = hexArray[v >>> 4];
                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating cache key: {}", e.getMessage());
            // fallback: direct key (may potentially expose internal details if logged in error)
            return rawKey;
        }
    }
}