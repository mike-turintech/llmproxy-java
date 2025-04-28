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
}
