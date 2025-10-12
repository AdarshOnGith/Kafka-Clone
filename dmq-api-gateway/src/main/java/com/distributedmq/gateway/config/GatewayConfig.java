package com.distributedmq.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Gateway Route Configuration.
 * 
 * Routes:
 * - /api/produce/** → Producer Ingestion Service
 * - /api/consume/** → Consumer Egress Service
 * - /api/metadata/** → Metadata Service
 * - /api/storage/** → Storage Service (admin only)
 * - /api/controller/** → Controller Service (admin only)
 * - /api/auth/** → Gateway Auth endpoints (public)
 * 
 * Features:
 * - Load balancing via Consul
 * - Circuit breaker
 * - Rate limiting
 * - Retry logic
 * - Request/response transformation
 */
@Slf4j
@Configuration
public class GatewayConfig {
    
    /**
     * Programmatic route configuration (alternative to YAML).
     * Commented out since we're using YAML configuration.
     * Kept for reference and advanced scenarios.
     */
    // @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Producer Ingestion Service
                .route("producer-ingestion", r -> r
                        .path("/api/produce/**")
                        .and()
                        .method(HttpMethod.POST)
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(c -> c
                                        .setName("producerCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/producer"))
                                .retry(config -> config
                                        .setRetries(3)
                                        .setBackoff(100, 1000, 2, false)))
                        .uri("lb://dmq-producer-ingestion"))
                
                // Consumer Egress Service
                .route("consumer-egress", r -> r
                        .path("/api/consume/**")
                        .and()
                        .method(HttpMethod.GET)
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(c -> c
                                        .setName("consumerCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/consumer")))
                        .uri("lb://dmq-consumer-egress"))
                
                .build();
    }
}
