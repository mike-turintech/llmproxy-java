package com.llmproxy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {
    
    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(options -> options
                        .setConnectTimeout(Duration.ofSeconds(10))
                        .setReadTimeout(Duration.ofSeconds(30)))
                .build();
    }
}
