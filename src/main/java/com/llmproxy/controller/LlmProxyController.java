package com.llmproxy.controller;

import com.llmproxy.exception.ModelError;
import com.llmproxy.model.ModelType;
import com.llmproxy.model.QueryRequest;
import com.llmproxy.model.QueryResponse;
import com.llmproxy.model.StatusResponse;
import com.llmproxy.service.cache.CacheService;
import com.llmproxy.service.llm.LlmClient;
import com.llmproxy.service.llm.LlmClientFactory;
import com.llmproxy.service.llm.QueryResult;
import com.llmproxy.service.ratelimit.RateLimiterService;
import com.llmproxy.service.router.RouterService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class LlmProxyController {
    private static final int MAX_QUERY_LENGTH = 32000;
    
    private final RouterService routerService;
    private final LlmClientFactory clientFactory;
    private final CacheService cacheService;
    private final RateLimiterService rateLimiterService;
    
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiterService.allowClient(clientIp)) {
            log.warn("Rate limit exceeded for client: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(QueryResponse.builder()
                            .error("Rate limit exceeded. Please try again later.")
                            .errorType("rate_limit")
                            .timestamp(Instant.now())
                            .build());
        }
        
        if (request.getQuery() == null || request.getQuery().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(QueryResponse.builder()
                            .error("Query cannot be empty")
                            .errorType("validation_error")
                            .timestamp(Instant.now())
                            .build());
        }
        
        if (request.getQuery().length() > MAX_QUERY_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(QueryResponse.builder()
                            .error("Query exceeds maximum length of " + MAX_QUERY_LENGTH + " characters")
                            .errorType("validation_error")
                            .timestamp(Instant.now())
                            .build());
        }
        
        if (request.getRequestId() == null || request.getRequestId().isEmpty()) {
            request.setRequestId(UUID.randomUUID().toString());
        }
        
        request.setQuery(request.getQuery().trim());
        
        log.info("Processing query request: model={}, taskType={}, requestId={}",
                request.getModel(), request.getTaskType(), request.getRequestId());
        
        QueryResponse cachedResponse = cacheService.get(request);
        if (cachedResponse != null) {
            log.info("Returning cached response for requestId={}", request.getRequestId());
            return ResponseEntity.ok(cachedResponse);
        }
        
        long startTime = Instant.now().toEpochMilli();
        
        try {
            ModelType modelType = routerService.routeRequest(request);
            LlmClient client = clientFactory.getClient(modelType);
            
            QueryResult result = client.query(request.getQuery(), request.getModelVersion());
            
            QueryResponse response = QueryResponse.builder()
                    .response(result.getResponse())
                    .model(modelType)
                    .responseTimeMs(Instant.now().toEpochMilli() - startTime)
                    .timestamp(Instant.now())
                    .cached(false)
                    .requestId(request.getRequestId())
                    .inputTokens(result.getInputTokens())
                    .outputTokens(result.getOutputTokens())
                    .totalTokens(result.getTotalTokens())
                    .numTokens(result.getNumTokens())
                    .numRetries(result.getNumRetries())
                    .build();
            
            cacheService.set(request, response);
            
            log.info("Query completed: model={}, responseTime={}ms, tokens={}, requestId={}",
                    modelType, response.getResponseTimeMs(), response.getTotalTokens(), request.getRequestId());
            
            return ResponseEntity.ok(response);
            
        } catch (ModelError e) {
            if (e.isRetryable()) {
                try {
                    ModelType fallbackModel = routerService.fallbackOnError(
                            ModelType.fromString(e.getModel()), request, e);
                    
                    LlmClient fallbackClient = clientFactory.getClient(fallbackModel);
                    QueryResult result = fallbackClient.query(request.getQuery(), request.getModelVersion());
                    
                    QueryResponse response = QueryResponse.builder()
                            .response(result.getResponse())
                            .model(fallbackModel)
                            .originalModel(ModelType.fromString(e.getModel()))
                            .responseTimeMs(Instant.now().toEpochMilli() - startTime)
                            .timestamp(Instant.now())
                            .cached(false)
                            .requestId(request.getRequestId())
                            .inputTokens(result.getInputTokens())
                            .outputTokens(result.getOutputTokens())
                            .totalTokens(result.getTotalTokens())
                            .numTokens(result.getNumTokens())
                            .numRetries(result.getNumRetries())
                            .build();
                    
                    cacheService.set(request, response);
                    
                    log.info("Fallback query completed: originalModel={}, fallbackModel={}, responseTime={}ms, requestId={}",
                            e.getModel(), fallbackModel, response.getResponseTimeMs(), request.getRequestId());
                    
                    return ResponseEntity.ok(response);
                    
                } catch (Exception fallbackError) {
                    log.error("Fallback failed: {}", fallbackError.getMessage());
                }
            }
            
            log.error("Error processing query: {}", e.getMessage());
            
            HttpStatus status = switch (e.getStatusCode()) {
                case 401 -> HttpStatus.UNAUTHORIZED;
                case 408 -> HttpStatus.REQUEST_TIMEOUT;
                case 429 -> HttpStatus.TOO_MANY_REQUESTS;
                case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
            
            return ResponseEntity.status(status)
                    .body(QueryResponse.builder()
                            .error(e.getMessage())
                            .errorType(e.getClass().getSimpleName())
                            .model(ModelType.fromString(e.getModel()))
                            .timestamp(Instant.now())
                            .requestId(request.getRequestId())
                            .build());
            
        } catch (Exception e) {
            log.error("Unexpected error processing query: {}", e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(QueryResponse.builder()
                            .error("Internal server error: " + e.getMessage())
                            .errorType("internal_error")
                            .timestamp(Instant.now())
                            .requestId(request.getRequestId())
                            .build());
        }
    }
    
    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status(HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiterService.allowClient(clientIp)) {
            log.warn("Rate limit exceeded for status check from client: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        
        StatusResponse status = routerService.getAvailability();
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health(HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiterService.allowClient(clientIp)) {
            log.warn("Rate limit exceeded for health check from client: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "timestamp", Instant.now()
        ));
    }
    
    @PostMapping("/download")
    public ResponseEntity<byte[]> download(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiterService.allowClient(clientIp)) {
            log.warn("Rate limit exceeded for download from client: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        
        String response = request.get("response");
        String format = request.get("format");
        
        if (response == null || response.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        if (format == null) {
            format = "txt";
        }
        
        MediaType mediaType;
        String filename;
        
        switch (format.toLowerCase()) {
            case "txt":
                mediaType = MediaType.TEXT_PLAIN;
                filename = "llm_response.txt";
                break;
            case "pdf":
                mediaType = MediaType.APPLICATION_PDF;
                filename = "llm_response.pdf";
                break;
            case "docx":
                mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                filename = "llm_response.docx";
                break;
            default:
                return ResponseEntity.badRequest().body(
                        "Unsupported format. Supported formats are: txt, pdf, docx.".getBytes());
        }
        
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", "attachment; filename=" + filename)
                .body(response.getBytes());
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
