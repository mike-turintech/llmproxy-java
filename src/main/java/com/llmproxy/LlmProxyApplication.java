package com.llmproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class LlmProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmProxyApplication.class, args);
    }
}
