package com.llmproxy.service.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    @Test
    void allow_withinLimit_returnsTrue() {
        RateLimiterService limiter = new RateLimiterService(60, 10);
        
        assertTrue(limiter.allow());
    }

    @Test
    void allow_exceedsLimit_returnsFalse() {
        RateLimiterService limiter = new RateLimiterService(60, 3);
        
        assertTrue(limiter.allow());
        assertTrue(limiter.allow());
        assertTrue(limiter.allow());
        
        assertFalse(limiter.allow());
    }

    @Test
    void allowClient_withinLimit_returnsTrue() {
        RateLimiterService limiter = new RateLimiterService(60, 10);
        
        assertTrue(limiter.allowClient("client1"));
    }

    @Test
    void allowClient_differentClients_separateLimits() {
        RateLimiterService limiter = new RateLimiterService(60, 2);
        
        assertTrue(limiter.allowClient("client1"));
        assertTrue(limiter.allowClient("client1"));
        assertFalse(limiter.allowClient("client1"));
        
        assertTrue(limiter.allowClient("client2"));
        assertTrue(limiter.allowClient("client2"));
        assertFalse(limiter.allowClient("client2"));
    }

    @Test
    void allowClient_withCustomFunction_usesFunction() {
        RateLimiterService limiter = new RateLimiterService(60, 10);
        
        AtomicInteger callCount = new AtomicInteger(0);
        Function<String, Boolean> customFunc = clientId -> {
            callCount.incrementAndGet();
            return "allowed".equals(clientId);
        };
        
        limiter.setAllowClientFunc(customFunc);
        
        assertTrue(limiter.allowClient("allowed"));
        assertFalse(limiter.allowClient("blocked"));
        assertEquals(2, callCount.get());
    }

    @Test
    void allow_zeroRefillRate_tokensDoNotRefill() {
        RateLimiterService limiter = new RateLimiterService(0, 3); // 0 requests per minute, burst 3
        
        assertTrue(limiter.allow()); // Consumes 1st token
        assertTrue(limiter.allow()); // Consumes 2nd token
        assertTrue(limiter.allow()); // Consumes 3rd token
        
        // All tokens consumed, and refill rate is 0
        assertFalse(limiter.allow()); // Should be false as no tokens can be refilled
        assertFalse(limiter.allow()); // Should remain false
    }

    @Test
    void allowClient_zeroRefillRate_clientTokensDoNotRefill() {
        // Parent limiter has 0 refill rate, so client-specific limiters will also have 0 refill rate.
        RateLimiterService parentLimiter = new RateLimiterService(0, 2); 

        // Test for client1
        assertTrue(parentLimiter.allowClient("client1")); // Uses 1st token for client1
        assertTrue(parentLimiter.allowClient("client1")); // Uses 2nd token for client1
        assertFalse(parentLimiter.allowClient("client1"));// No more tokens for client1, should not refill
        assertFalse(parentLimiter.allowClient("client1"));// Still no tokens for client1

        // Test for client2 (should be independent but also not refill)
        assertTrue(parentLimiter.allowClient("client2")); // Uses 1st token for client2
        assertTrue(parentLimiter.allowClient("client2")); // Uses 2nd token for client2
        assertFalse(parentLimiter.allowClient("client2"));// No more tokens for client2, should not refill
    }

    @Test
    void allow_zeroBurst_alwaysReturnsFalse() {
        RateLimiterService limiter = new RateLimiterService(60, 0); // 60 requests per minute, burst 0
        
        // With zero burst capacity, no tokens are ever available.
        assertFalse(limiter.allow());
        assertFalse(limiter.allow()); // Should remain false even if time passes, as maxTokens is 0.
    }

    @Test
    void allowClient_zeroBurst_alwaysReturnsFalseForClients() {
        // Parent limiter has 0 burst, so client-specific limiters will also have 0 burst.
        RateLimiterService parentLimiter = new RateLimiterService(60, 0); 

        // Test for client1
        assertFalse(parentLimiter.allowClient("client1")); // Client limiter inherits zero burst
        assertFalse(parentLimiter.allowClient("client1"));

        // Test for client2
        assertFalse(parentLimiter.allowClient("client2")); // Client limiter inherits zero burst
    }
}