package com.distributedmq.client.test;

import com.distributedmq.client.producer.DMQProducer;
import com.distributedmq.client.producer.ProducerConfig;
import com.distributedmq.common.dto.ProduceResponse;

import java.util.concurrent.Future;

/**
 * Integration test to send messages to friend's storage service
 * 
 * This test will:
 * 1. Fetch metadata from mock server (localhost:8081)
 * 2. Select partition for "orders" topic
 * 3. Send message to friend's storage service (https://qwmrnzsf-8082.inc1.devtunnels.ms)
 */
public class TestIntegrationWithFriendStorage {

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Integration Test: Producer → Friend's Storage Service       ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();

        try {
            // Create producer config
            String metadataServiceUrl = "http://localhost:8081";
            ProducerConfig config = ProducerConfig.builder()
                    .metadataServiceUrl(metadataServiceUrl)
                    .build();
            
            DMQProducer producer = new DMQProducer(config);
            System.out.println("✅ Producer created with metadata service: " + metadataServiceUrl);
            System.out.println();

            // Test 1: Send message to "orders" topic
            System.out.println("📤 TEST 1: Sending message to 'orders' topic");
            System.out.println("   Key: user123");
            System.out.println("   Value: Hello World from Viral's Producer!");
            System.out.println();

            String topic = "orders";
            Integer partitionId = 0; // Explicitly use partition 0 (friend's dev tunnel)
            String key = "user123";
            String value = "Hello World from Viral's Producer!";
            byte[] valueBytes = value.getBytes();

            // Send with explicit partition to ensure it goes to friend's storage service
            Future<ProduceResponse> future = producer.send(topic, partitionId, key, valueBytes);
            ProduceResponse response = future.get(); // Wait for response

            System.out.println("📨 Response received:");
            System.out.println("   Success: " + response.isSuccess());
            System.out.println("   Topic: " + response.getTopic());
            System.out.println("   Partition: " + response.getPartition());
            
            // Get offset from results
            Long offset = null;
            if (response.getResults() != null && !response.getResults().isEmpty()) {
                offset = response.getResults().get(0).getOffset();
                System.out.println("   Offset: " + offset);
            }
            
            if (response.getErrorMessage() != null) {
                System.out.println("   Error: " + response.getErrorMessage());
            }
            System.out.println();

            if (response.isSuccess()) {
                System.out.println("✅ SUCCESS! Message sent to friend's storage service!");
                System.out.println("   Assigned offset: " + offset);
            } else {
                System.out.println("❌ FAILED! Message not sent.");
                System.out.println("   Error: " + response.getErrorMessage());
            }
            System.out.println();

            // Test 2: Send another message (also to partition 0)
            System.out.println("📤 TEST 2: Sending another message (also to partition 0)");
            System.out.println("   Key: user456");
            System.out.println("   Value: Second message from Viral!");
            System.out.println();

            Future<ProduceResponse> future2 = producer.send("orders", 0, "user456", "Second message from Viral!".getBytes());
            ProduceResponse response2 = future2.get(); // Wait for response

            System.out.println("📨 Response received:");
            System.out.println("   Success: " + response2.isSuccess());
            System.out.println("   Partition: " + response2.getPartition());
            
            Long offset2 = null;
            if (response2.getResults() != null && !response2.getResults().isEmpty()) {
                offset2 = response2.getResults().get(0).getOffset();
                System.out.println("   Offset: " + offset2);
            }
            System.out.println();

            if (response2.isSuccess()) {
                System.out.println("✅ SUCCESS! Second message sent!");
            }

            System.out.println();
            System.out.println("╔═══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Integration Test Complete!                                   ║");
            System.out.println("╚═══════════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("❌ Test failed with exception:");
            e.printStackTrace();
        }
    }
}
