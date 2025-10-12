package com.distributedmq.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global logging filter for request/response logging.
 * Logs request details and response time.
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        long startTime = System.currentTimeMillis();
        String requestId = request.getHeaders().getFirst("X-Request-Id");
        
        log.info("➡️ Incoming request: {} {} | RequestId: {} | RemoteAddr: {}",
                request.getMethod(),
                request.getURI().getPath(),
                requestId,
                request.getRemoteAddress());
        
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("⬅️ Outgoing response: {} | Status: {} | Duration: {}ms | RequestId: {}",
                    request.getURI().getPath(),
                    response.getStatusCode(),
                    duration,
                    requestId);
            
            // Add response time header
            response.getHeaders().add("X-Response-Time", duration + "ms");
        }));
    }
    
    @Override
    public int getOrder() {
        return -99; // Execute after auth filter
    }
}
