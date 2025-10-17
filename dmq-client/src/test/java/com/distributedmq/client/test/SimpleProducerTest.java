package com.distributedmq.client.test;

import com.distributedmq.client.producer.DMQProducer;
import com.distributedmq.client.producer.ProducerConfig;
import com.distributedmq.common.dto.ProduceResponse;

import java.util.concurrent.Future;

/**
 * Simple manual test to verify producer works with real services
 * Run this after starting metadata service (8081) and at least one storage service (8082)
 */
public class SimpleProducerTest {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   Simple Producer Test with Services  ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        
        try {
            // Create producer
            ProducerConfig config = ProducerConfig.builder()
                    .metadataServiceUrl("http://localhost:8081")
                    .build();

            DMQProducer producer = new DMQProducer(config);

            // Send a message
            System.out.println("📤 Sending message to test-topic...");
            Future<ProduceResponse> future = producer.send(
                    "test-topic",
                    "simple-test-key",
                    "Hello from simple producer test!".getBytes()
            );

            // Wait for response
            ProduceResponse response = future.get();

            // Display results
            System.out.println();
            System.out.println("═══════════════════════════════════════");
            System.out.println("         RESULT");
            System.out.println("═══════════════════════════════════════");
            System.out.println("Success: " + response.isSuccess());
            System.out.println("Topic: " + response.getTopic());
            System.out.println("Partition: " + response.getPartition());
            
            if (response.getResults() != null && !response.getResults().isEmpty()) {
                System.out.println("Offset: " + response.getResults().get(0).getOffset());
                System.out.println("Timestamp: " + response.getResults().get(0).getTimestamp());
            }
            
            if (response.getErrorMessage() != null) {
                System.out.println("Error: " + response.getErrorMessage());
            }

            producer.close();

        } catch (Exception e) {
            System.err.println();
            System.err.println("═══════════════════════════════════════");
            System.err.println("         TEST FAILED");
            System.err.println("═══════════════════════════════════════");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
