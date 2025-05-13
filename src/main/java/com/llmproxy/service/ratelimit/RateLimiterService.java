package com.llmproxy.service.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Service
@Slf4j
public class RateLimiterService {
    private double tokens;
    private long lastRefillMs; // Using milliseconds instead of Instant for better performance
    private final double refillRate;
    private final double maxTokens;
    private final Map<String, RateLimiterService> clientLimiters = new ConcurrentHashMap<>();
    private Function<String, Boolean> allowClientFunc;
    private static final int MAX_CLIENTS = 10000;
    private long lastCleanupMs;
    private static final long CLEANUP_INTERVAL_MS = 300000; // 5 minutes
    
    public RateLimiterService(
            @Value("${rate-limit.requests-per-minute:60}") int requestsPerMinute,
            @Value("${rate-limit.burst:10}") int burst) {
        this.tokens = burst;
        this.lastRefillMs = System.currentTimeMillis();
        this.lastCleanupMs = this.lastRefillMs;
        this.refillRate = (double) requestsPerMinute / 60.0; // Convert to per-second
        this.maxTokens = burst;
    }
    
    public synchronized boolean allow() {
        long nowMs = System.currentTimeMillis();
        double elapsed = (nowMs - lastRefillMs) / 1000.0;
        tokens = Math.min(maxTokens, tokens + elapsed * refillRate);
        lastRefillMs = nowMs;
        
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }
    
    public boolean allowClient(String clientId) {
        if (allowClientFunc != null) {
            return allowClientFunc.apply(clientId);
        }
        
        // Check if cleanup is needed
        maybeCleanupClients();
        
        RateLimiterService clientLimiter = clientLimiters.computeIfAbsent(clientId, 
            id -> new RateLimiterService((int) (refillRate * 60), (int) maxTokens));
        
        return clientLimiter.allow();
    }
    
    public void setAllowClientFunc(Function<String, Boolean> func) {
        this.allowClientFunc = func;
    }
    
    private void maybeCleanupClients() {
        // Only check periodically to avoid overhead
        long now = System.currentTimeMillis();
        if (clientLimiters.size() > MAX_CLIENTS || 
            (clientLimiters.size() > 100 && now - lastCleanupMs > CLEANUP_INTERVAL_MS)) {
            
            lastCleanupMs = now;
            
            // Simple approach: just clear half of the map when it gets too large
            // In a more sophisticated implementation, we could track last access time
            if (clientLimiters.size() > MAX_CLIENTS / 2) {
                int toRemove = clientLimiters.size() / 2;
                clientLimiters.keySet().stream()
                    .limit(toRemove)
                    .forEach(clientLimiters::remove);
                
                log.info("Cleaned up {} client rate limiters, {} remaining", 
                         toRemove, clientLimiters.size());
            }
        }
    }
}