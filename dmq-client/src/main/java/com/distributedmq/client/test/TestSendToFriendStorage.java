package com.distributedmq.client.test;

import com.distributedmq.client.producer.DMQProducer;
import com.distributedmq.client.producer.ProducerConfig;
import com.distributedmq.common.dto.ProduceResponse;

import java.util.concurrent.Future;

/**
 * Simple test to send message to friend's storage service (partition 0)
 * This explicitly sends to partition 0 which is hosted on the dev tunnel
 */
public class TestSendToFriendStorage {

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Sending Message to Friend's Storage Service (Partition 0)   ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();

        try {
            // Create producer
            ProducerConfig config = ProducerConfig.builder()
                    .metadataServiceUrl("http://localhost:8081")
                    .build();
            
            DMQProducer producer = new DMQProducer(config);
            System.out.println("✅ Producer initialized");
            System.out.println();

            // Send message to partition 0 explicitly (friend's dev tunnel)
            System.out.println("📤 Sending message to 'orders' topic, partition 0");
            System.out.println("   (This will go to: https://qwmrnzsf-8082.inc1.devtunnels.ms)");
            System.out.println();

            String topic = "orders";
            Integer partition = 0; // Explicitly send to partition 0
            String key = "test-key";
            String value = "Hello from Viral's Producer! 🚀";

            Future<ProduceResponse> future = producer.send(topic, partition, key, value.getBytes());
            ProduceResponse response = future.get();

            System.out.println("📨 Response received:");
            System.out.println("   Success: " + response.isSuccess());
            System.out.println("   Topic: " + response.getTopic());
            System.out.println("   Partition: " + response.getPartition());
            
            if (response.getResults() != null && !response.getResults().isEmpty()) {
                Long offset = response.getResults().get(0).getOffset();
                System.out.println("   Offset: " + offset);
            }
            
            if (response.getErrorMessage() != null) {
                System.out.println("   Error: " + response.getErrorMessage());
            }
            System.out.println();

            if (response.isSuccess()) {
                System.out.println("✅ SUCCESS! Message sent to friend's storage service!");
                System.out.println("   Your message was successfully stored in the cluster! 🎉");
            } else {
                System.out.println("❌ FAILED! Message not sent.");
                System.out.println("   Error: " + response.getErrorMessage());
            }

        } catch (Exception e) {
            System.err.println("❌ Test failed:");
            e.printStackTrace();
        }
    }
}
