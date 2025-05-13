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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class LlmProxyController {
    private static final int MAX_QUERY_LENGTH = 32000;
    private static final String RATE_LIMIT_ERROR = "Rate limit exceeded. Please try again later.";
    private static final String EMPTY_QUERY_ERROR = "Query cannot be empty";
    private static final String VALIDATION_ERROR_TYPE = "validation_error";
    private static final String RATE_LIMIT_ERROR_TYPE = "rate_limit";
    private static final String INTERNAL_ERROR_TYPE = "internal_error";
    
    // Cache MediaType objects
    private static final MediaType TEXT_PLAIN = MediaType.TEXT_PLAIN;
    private static final MediaType APPLICATION_PDF = MediaType.APPLICATION_PDF;
    private static final MediaType APPLICATION_DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    
    private final RouterService routerService;
    private final LlmClientFactory clientFactory;
    private final CacheService cacheService;
    private final RateLimiterService rateLimiterService;
    
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiterService.allowClient(clientIp)) {
            log.warn("Rate limit exceeded for client: {}", clientIp);
            Instant now = Instant.now();
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(QueryResponse.builder()
                            .error(RATE_LIMIT_ERROR)
                            .errorType(RATE_LIMIT_ERROR_TYPE)
                            .timestamp(now)
                            .build());
        }
        
        if (request.getQuery() == null || request.getQuery().isEmpty()) {
            Instant now = Instant.now();
            return ResponseEntity.badRequest()
                    .body(QueryResponse.builder()
                            .error(EMPTY_QUERY_ERROR)
                            .errorType(VALIDATION_ERROR_TYPE)
                            .timestamp(now)
                            .build());
        }
        
        if (request.getQuery().length() > MAX_QUERY_LENGTH) {
            Instant now = Instant.now();
            return ResponseEntity.badRequest()
                    .body(QueryResponse.builder()
                            .error("Query exceeds maximum length of " + MAX_QUERY_LENGTH + " characters")
                            .errorType(VALIDATION_ERROR_TYPE)
                            .timestamp(now)
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
            
            Instant responseTime = Instant.now();
            
            QueryResponse response = QueryResponse.builder()
                    .response(result.getResponse())
                    .model(modelType)
                    .responseTimeMs(responseTime.toEpochMilli() - startTime)
                    .timestamp(responseTime)
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
                    
                    Instant responseTime = Instant.now();
                    
                    QueryResponse response = QueryResponse.builder()
                            .response(result.getResponse())
                            .model(fallbackModel)
                            .originalModel(ModelType.fromString(e.getModel()))
                            .responseTimeMs(responseTime.toEpochMilli() - startTime)
                            .timestamp(responseTime)
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
            
            HttpStatus status;
            switch (e.getStatusCode()) {
                case 401:
                    status = HttpStatus.UNAUTHORIZED;
                    break;
                case 408:
                    status = HttpStatus.REQUEST_TIMEOUT;
                    break;
                case 429:
                    status = HttpStatus.TOO_MANY_REQUESTS;
                    break;
                case 503:
                    status = HttpStatus.SERVICE_UNAVAILABLE;
                    break;
                default:
                    status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            
            Instant errorTime = Instant.now();
            return ResponseEntity.status(status)
                    .body(QueryResponse.builder()
                            .error(e.getMessage())
                            .errorType(e.getClass().getSimpleName())
                            .model(ModelType.fromString(e.getModel()))
                            .timestamp(errorTime)
                            .requestId(request.getRequestId())
                            .build());
            
        } catch (Exception e) {
            log.error("Unexpected error processing query: {}", e.getMessage(), e);
            
            Instant errorTime = Instant.now();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(QueryResponse.builder()
                            .error("Internal server error: " + e.getMessage())
                            .errorType(INTERNAL_ERROR_TYPE)
                            .timestamp(errorTime)
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
        
        Instant now = Instant.now();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "timestamp", now
        ));
    }
    
    @PostMapping("/download")
    public ResponseEntity<byte[]> download(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiterService.allowClient(clientIp)) {
            log.warn("Rate limit exceeded for download from client: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        
        String responseText = request.get("response");
        if (responseText == null || responseText.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        String format = request.get("format");
        if (format == null) {
            format = "txt";
        }
        
        byte[] responseBytes = responseText.getBytes(StandardCharsets.UTF_8);
        
        MediaType mediaType;
        String filename;
        
        switch (format.toLowerCase()) {
            case "txt":
                mediaType = TEXT_PLAIN;
                filename = "llm_response.txt";
                break;
            case "pdf":
                mediaType = APPLICATION_PDF;
                filename = "llm_response.pdf";
                break;
            case "docx":
                mediaType = APPLICATION_DOCX;
                filename = "llm_response.docx";
                break;
            default:
                return ResponseEntity.badRequest().body(
                        "Unsupported format. Supported formats are: txt, pdf, docx.".getBytes(StandardCharsets.UTF_8));
        }
        
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", "attachment; filename=" + filename)
                .body(responseBytes);
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}