package com.distributedmq.client.test;

import com.distributedmq.client.producer.DMQProducer;
import com.distributedmq.client.producer.ProducerConfig;
import com.distributedmq.common.dto.ProduceResponse;

import java.util.concurrent.Future;

/**
 * Test application for Phase 1: Metadata Service Integration
 * Tests producer's ability to fetch topic metadata
 */
public class TestMetadataFetch {
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║  DMQ Producer - Metadata Fetch Test   ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        
        try {
            // Load configuration from file
            System.out.println("[INFO] Loading producer configuration...");
            ProducerConfig config = ProducerConfig.loadFromResource("producer-config.properties");
            System.out.println("✅ Configuration loaded");
            System.out.println("   Metadata Service URL: " + config.getMetadataServiceUrl());
            System.out.println();
            
            // Create producer
            System.out.println("[INFO] Creating DMQ Producer...");
            DMQProducer producer = new DMQProducer(config);
            System.out.println("✅ Producer created");
            System.out.println();
            
            // Test metadata fetch by sending a message
            System.out.println("[INFO] Testing metadata fetch for topic 'test-topic'...");
            System.out.println();
            
            Future<ProduceResponse> future = producer.send(
                    "test-topic",
                    "user-123",
                    "Hello from DMQ Producer!".getBytes()
            );
            
            // Wait for response
            ProduceResponse response = future.get();
            
            System.out.println();
            System.out.println("═══════════════════════════════════════");
            System.out.println("         TEST COMPLETED");
            System.out.println("═══════════════════════════════════════");
            System.out.println("✅ Metadata fetch successful!");
            System.out.println("   Response: " + response);
            
            // Close producer
            producer.close();
            System.out.println("✅ Producer closed");
            
        } catch (Exception e) {
            System.err.println();
            System.err.println("═══════════════════════════════════════");
            System.err.println("         TEST FAILED");
            System.err.println("═══════════════════════════════════════");
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
