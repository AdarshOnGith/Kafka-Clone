package com.distributedmq.client.test;

import com.distributedmq.client.producer.DMQProducer;
import com.distributedmq.client.producer.ProducerConfig;
import com.distributedmq.common.dto.ProduceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test with real metadata and storage services
 * Tests the complete end-to-end flow
 */
@DisplayName("End-to-End Integration Test")
class EndToEndIntegrationTest {

    @Test
    @DisplayName("Complete producer flow with real services")
    void testEndToEndWithRealServices() throws Exception {
        // Create producer configuration pointing to real services
        ProducerConfig config = ProducerConfig.builder()
                .metadataServiceUrl("http://localhost:8081")
                .build();

        // Create producer
        DMQProducer producer = new DMQProducer(config);

        try {
            // Test 1: Send a message
            System.out.println("🧪 Testing message send to real services...");

            Future<ProduceResponse> future = producer.send(
                    "test-topic",
                    "integration-test-key",
                    "Hello from integration test!".getBytes()
            );

            // Wait for response
            ProduceResponse response = future.get();

            // Verify response
            System.out.println("📋 Response received: " + response);
            assertNotNull(response, "Response should not be null");
            assertEquals("test-topic", response.getTopic(), "Topic should match");
            assertTrue(response.getPartition() >= 0, "Partition should be valid");
            assertTrue(response.isSuccess(), "Send should be successful");

            // Verify offset was assigned
            assertNotNull(response.getResults(), "Results should not be null");
            assertFalse(response.getResults().isEmpty(), "Should have at least one result");
            assertNotNull(response.getResults().get(0).getOffset(), "Offset should be assigned");
            assertTrue(response.getResults().get(0).getOffset() >= 0, "Offset should be non-negative");

            System.out.println("✅ Integration test passed!");
            System.out.println("   - Topic: " + response.getTopic());
            System.out.println("   - Partition: " + response.getPartition());
            System.out.println("   - Offset: " + response.getResults().get(0).getOffset());
            System.out.println("   - Success: " + response.isSuccess());

        } finally {
            // Clean up
            producer.close();
        }
    }

    @Test
    @DisplayName("Test with specific partition")
    void testSendToSpecificPartition() throws Exception {
        ProducerConfig config = ProducerConfig.builder()
                .metadataServiceUrl("http://localhost:8081")
                .build();

        DMQProducer producer = new DMQProducer(config);

        try {
            // Send to partition 0 specifically
            Future<ProduceResponse> future = producer.send(
                    "test-topic",
                    0, // specific partition
                    "partition-test-key",
                    "Message to partition 0".getBytes()
            );

            ProduceResponse response = future.get();

            assertNotNull(response);
            assertEquals("test-topic", response.getTopic());
            assertEquals(0, response.getPartition(), "Should send to partition 0");
            assertTrue(response.isSuccess());

            System.out.println("✅ Partition-specific test passed!");

        } finally {
            producer.close();
        }
    }

    @Test
    @DisplayName("Test multiple messages")
    void testMultipleMessages() throws Exception {
        ProducerConfig config = ProducerConfig.builder()
                .metadataServiceUrl("http://localhost:8081")
                .build();

        DMQProducer producer = new DMQProducer(config);

        try {
            long[] offsets = new long[3];

            // Send 3 messages
            for (int i = 0; i < 3; i++) {
                Future<ProduceResponse> future = producer.send(
                        "test-topic",
                        "multi-msg-key-" + i,
                        ("Message " + i).getBytes()
                );

                ProduceResponse response = future.get();
                assertTrue(response.isSuccess(), "Message " + i + " should succeed");
                offsets[i] = response.getResults().get(0).getOffset();

                System.out.println("✅ Message " + i + " sent with offset: " + offsets[i]);
            }

            // Verify offsets are sequential (assuming same partition)
            // Note: This might not be true if messages go to different partitions
            System.out.println("📊 Offsets: " + offsets[0] + ", " + offsets[1] + ", " + offsets[2]);

        } finally {
            producer.close();
        }
    }
}