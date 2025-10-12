package com.distributedmq.controller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Controller Service Application.
 * 
 * Responsibilities:
 * - Monitor storage node health (Flow 3 Step 1)
 * - Detect storage node failures (Flow 3 Step 2)
 * - Elect new partition leaders (Flow 3 Step 3)
 * - Update partition metadata (Flow 3 Step 4)
 * - Coordinate cluster-wide operations
 * 
 * Architecture:
 * - Leader election using etcd
 * - Scheduled failure detection
 * - Metadata Service integration
 * - Event-driven partition reassignment
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableAsync
public class ControllerServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ControllerServiceApplication.class, args);
    }
}
