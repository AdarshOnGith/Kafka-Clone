package com.distributedmq.gateway.config;

import com.distributedmq.gateway.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

/**
 * Rate Limiting Configuration.
 * Uses Redis-backed token bucket algorithm.
 * 
 * Key Resolution Strategies:
 * 1. User-based: Extract user from JWT (default)
 * 2. IP-based: Use client IP address
 * 3. API-key-based: Use API key header
 */
@Slf4j
@Configuration
public class RateLimitConfig {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * User-based key resolver (default).
     * Extracts username from JWT token.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            ServerHttpRequest request = exchange.getRequest();
            String authHeader = request.getHeaders().getFirst("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                try {
                    String username = jwtUtil.extractUsername(token);
                    String tier = jwtUtil.extractTier(token);
                    
                    // Use username + tier as key for different rate limits
                    String key = username + ":" + tier;
                    log.trace("Rate limit key: {}", key);
                    return Mono.just(key);
                    
                } catch (Exception e) {
                    log.debug("Failed to extract user from JWT: {}", e.getMessage());
                }
            }
            
            // Fallback to IP address for unauthenticated requests
            String clientIp = getClientIp(request);
            log.trace("Rate limit key (fallback to IP): {}", clientIp);
            return Mono.just(clientIp);
        };
    }
    
    /**
     * IP-based key resolver.
     * Uses client IP address for rate limiting.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String clientIp = getClientIp(exchange.getRequest());
            log.trace("IP-based rate limit key: {}", clientIp);
            return Mono.just(clientIp);
        };
    }
    
    /**
     * API key-based resolver.
     * Uses X-API-Key header for rate limiting.
     */
    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> {
            String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
            
            if (apiKey != null && !apiKey.isEmpty()) {
                log.trace("API key-based rate limit key: {}", apiKey);
                return Mono.just(apiKey);
            }
            
            // Fallback to IP
            String clientIp = getClientIp(exchange.getRequest());
            log.trace("API key-based rate limit key (fallback to IP): {}", clientIp);
            return Mono.just(clientIp);
        };
    }
    
    /**
     * Extract client IP address from request.
     * Handles X-Forwarded-For header for proxy scenarios.
     */
    private String getClientIp(ServerHttpRequest request) {
        // Check X-Forwarded-For header first
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            // Return first IP in the chain
            return xff.split(",")[0].trim();
        }
        
        // Check X-Real-IP header
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // Fallback to remote address
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }
}
