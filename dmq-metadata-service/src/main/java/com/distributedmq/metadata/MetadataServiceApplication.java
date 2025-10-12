package com.distributedmq.metadata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Metadata Service Application.
 * 
 * Responsibilities:
 * - Manage topic and partition metadata
 * - Track consumer group state and offsets
 * - Monitor storage node health
 * - Provide metadata queries to other services
 * 
 * Architecture:
 * - REST API for client-facing operations
 * - gRPC API for inter-service communication
 * - PostgreSQL for durable metadata storage
 * - Caffeine cache for high-performance reads
 * - Consul for service discovery
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
@EnableScheduling
@EnableJpaAuditing
public class MetadataServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MetadataServiceApplication.class, args);
    }
}
