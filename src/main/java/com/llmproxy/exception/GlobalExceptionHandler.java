package com.llmproxy.exception;

import com.llmproxy.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(ModelError.class)
    public ResponseEntity<ErrorResponse> handleModelError(ModelError e) {
        logger.error("Model error: {}", e.getMessage());
        
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
        
        return new ResponseEntity<>(
                new ErrorResponse(e.getMessage()),
                status
        );
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        logger.error("Unexpected error: {}", e.getMessage(), e);
        
        return new ResponseEntity<>(
                new ErrorResponse("Internal server error"),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
