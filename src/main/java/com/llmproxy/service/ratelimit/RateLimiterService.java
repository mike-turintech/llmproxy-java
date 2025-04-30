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
    private Instant lastRefill;
    private final double refillRate;
    private final double maxTokens;
    private final Map<String, RateLimiterService> clientLimiters = new ConcurrentHashMap<>();
    private Function<String, Boolean> allowClientFunc;
    
    public RateLimiterService(
            @Value("${rate-limit.requests-per-minute:60}") int requestsPerMinute,
            @Value("${rate-limit.burst:10}") int burst) {
        this.tokens = burst;
        this.lastRefill = Instant.now();
        this.refillRate = (double) requestsPerMinute / 60.0; // Convert to per-second
        this.maxTokens = burst;
    }
    
    public synchronized boolean allow() {
        Instant now = Instant.now();
        double elapsed = (now.toEpochMilli() - lastRefill.toEpochMilli()) / 1000.0;
        tokens = Math.min(maxTokens, tokens + elapsed * refillRate);
        lastRefill = now;
        
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
        
        RateLimiterService clientLimiter = clientLimiters.computeIfAbsent(clientId, 
            id -> new RateLimiterService((int) (refillRate * 60), (int) maxTokens));
        
        return clientLimiter.allow();
    }
    
    public void setAllowClientFunc(Function<String, Boolean> func) {
        this.allowClientFunc = func;
    }
}
