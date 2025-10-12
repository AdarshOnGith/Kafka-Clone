package com.distributedmq.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Storage Service Application.
 * 
 * Responsibilities:
 * - Persist messages to Write-Ahead Log (WAL)
 * - Replicate messages to follower nodes
 * - Serve fetch requests for consumers
 * - Maintain In-Sync Replica (ISR) set
 * - Report status to Metadata Service
 * 
 * Architecture:
 * - Write-Ahead Log with segment files
 * - Leader/Follower replication protocol
 * - gRPC API for append and fetch operations
 * - Zero-copy file transfer for high throughput
 * - Consul for service discovery
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableAsync
public class StorageServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(StorageServiceApplication.class, args);
    }
}
