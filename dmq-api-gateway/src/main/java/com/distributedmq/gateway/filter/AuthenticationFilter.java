package com.distributedmq.gateway.filter;

import com.distributedmq.gateway.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global authentication filter for JWT validation.
 * 
 * Flow:
 * 1. Check if path is public (skip auth)
 * 2. Extract JWT from Authorization header
 * 3. Validate JWT token
 * 4. Extract user info and add to request headers
 * 5. Check admin-only paths
 */
@Slf4j
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Value("${dmq.gateway.auth.enabled:true}")
    private boolean authEnabled;
    
    @Value("#{'${dmq.gateway.auth.public-paths}'.split(',')}")
    private List<String> publicPaths;
    
    @Value("#{'${dmq.gateway.auth.admin-paths}'.split(',')}")
    private List<String> adminPaths;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        // Skip auth if disabled or path is public
        if (!authEnabled || isPublicPath(path)) {
            log.trace("Skipping auth for public path: {}", path);
            return chain.filter(exchange);
        }
        
        // Extract Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }
        
        String token = authHeader.substring(7);
        
        // Validate token
        if (!jwtUtil.validateToken(token)) {
            log.warn("Invalid JWT token for path: {}", path);
            return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }
        
        // Extract user info
        String username = jwtUtil.extractUsername(token);
        List<String> roles = jwtUtil.extractRoles(token);
        String tier = jwtUtil.extractTier(token);
        
        log.debug("Authenticated user: {}, roles: {}, tier: {}", username, roles, tier);
        
        // Check admin-only paths
        if (isAdminPath(path) && !roles.contains("ADMIN")) {
            log.warn("User {} attempted to access admin-only path: {}", username, path);
            return onError(exchange, "Access denied: admin role required", HttpStatus.FORBIDDEN);
        }
        
        // Add user info to request headers for downstream services
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Id", username)
                .header("X-User-Roles", String.join(",", roles))
                .header("X-User-Tier", tier)
                .build();
        
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }
    
    /**
     * Check if path is public (no auth required).
     */
    private boolean isPublicPath(String path) {
        return publicPaths.stream()
                .anyMatch(publicPath -> pathMatches(path, publicPath));
    }
    
    /**
     * Check if path is admin-only.
     */
    private boolean isAdminPath(String path) {
        return adminPaths.stream()
                .anyMatch(adminPath -> pathMatches(path, adminPath));
    }
    
    /**
     * Match path with pattern (supports wildcards).
     */
    private boolean pathMatches(String path, String pattern) {
        // Remove whitespace
        pattern = pattern.trim();
        
        // Simple wildcard matching
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        } else if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return path.startsWith(prefix) && !path.substring(prefix.length()).contains("/");
        } else {
            return path.equals(pattern);
        }
    }
    
    /**
     * Return error response.
     */
    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json");
        
        String errorBody = String.format("{\"error\":\"%s\",\"status\":%d}", message, status.value());
        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorBody.getBytes())));
    }
    
    @Override
    public int getOrder() {
        return -100; // Execute before other filters
    }
}
