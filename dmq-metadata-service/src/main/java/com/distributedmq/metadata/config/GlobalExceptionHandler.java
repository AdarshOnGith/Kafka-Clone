package com.distributedmq.metadata.config;

import com.distributedmq.common.exception.DMQException;
import com.distributedmq.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST endpoints.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(DMQException.class)
    public ResponseEntity<Map<String, Object>> handleDMQException(DMQException ex) {
        log.error("DMQ Exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("errorCode", ex.getCode());
        response.put("errorType", ex.getErrorCode().name());
        response.put("message", ex.getMessage());
        
        HttpStatus status = mapErrorCodeToHttpStatus(ex.getErrorCode());
        return ResponseEntity.status(status).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected exception", ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("errorCode", ErrorCode.UNKNOWN_ERROR.getCode());
        response.put("message", "An unexpected error occurred");
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    private HttpStatus mapErrorCodeToHttpStatus(ErrorCode errorCode) {
        int code = errorCode.getCode();
        
        if (code >= 1000 && code < 2000) {
            return HttpStatus.BAD_REQUEST;
        } else if (code >= 2000 && code < 3000) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        } else if (code >= 3000 && code < 4000) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        } else if (code >= 4000 && code < 5000) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
