package com.distributedmq.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Fallback Controller for circuit breaker.
 * Provides fallback responses when downstream services are unavailable.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {
    
    /**
     * Fallback for Producer Ingestion Service.
     */
    @PostMapping("/producer")
    public ResponseEntity<Map<String, Object>> producerFallback() {
        log.warn("⚠️ Circuit breaker activated for Producer Ingestion Service");
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Producer service temporarily unavailable");
        response.put("message", "Please try again later. Messages are being queued.");
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    /**
     * Fallback for Consumer Egress Service.
     */
    @GetMapping("/consumer")
    public ResponseEntity<Map<String, Object>> consumerFallback() {
        log.warn("⚠️ Circuit breaker activated for Consumer Egress Service");
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Consumer service temporarily unavailable");
        response.put("message", "Please try again later. Your offset has been preserved.");
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    /**
     * Generic fallback.
     */
    @GetMapping("/generic")
    @PostMapping("/generic")
    public ResponseEntity<Map<String, Object>> genericFallback() {
        log.warn("⚠️ Circuit breaker activated for service");
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Service temporarily unavailable");
        response.put("message", "Please try again later.");
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
