package com.distributedmq.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway Application.
 * 
 * Responsibilities:
 * - Route requests to appropriate microservices
 * - JWT-based authentication
 * - Rate limiting (Redis-backed)
 * - Circuit breaker for fault tolerance
 * - CORS handling
 * - Request/response logging
 * - Load balancing via Consul discovery
 * 
 * Architecture:
 * - Spring Cloud Gateway (reactive)
 * - Consul service discovery
 * - Redis for rate limiting
 * - Resilience4j for circuit breaker
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
