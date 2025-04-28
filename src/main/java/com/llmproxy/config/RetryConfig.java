package com.llmproxy.config;

import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RetryConfig {
    
    @Bean
    public RetryRegistry retryRegistry(
            @Value("${retry.max-attempts:3}") int maxAttempts,
            @Value("${retry.initial-backoff-ms:1000}") long initialBackoffMs,
            @Value("${retry.max-backoff-ms:30000}") long maxBackoffMs,
            @Value("${retry.backoff-multiplier:2.0}") double backoffMultiplier,
            @Value("${retry.jitter:0.1}") double jitter) {
        
        io.github.resilience4j.retry.RetryConfig config = io.github.resilience4j.retry.RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofMillis(initialBackoffMs))
                .retryExceptions(Exception.class)
                .build();
        
        return RetryRegistry.of(config);
    }
}
