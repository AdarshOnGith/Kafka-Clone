package com.distributedmq.client.test;

import com.distributedmq.common.dto.ProduceRequest;
import com.distributedmq.common.util.RequestFormatter;

import java.util.List;

/**
 * Test to verify the JSON format we're sending to storage service
 */
public class TestRequestFormat {

    public static void main(String[] args) {
        try {
            // Create a sample message (matching friend's test data)
            String key = "user123";
            String valueString = "Hello World";
            byte[] value = valueString.getBytes(); // Will be Base64 encoded by Jackson

            // Create the message
            ProduceRequest.ProduceMessage message = ProduceRequest.ProduceMessage.builder()
                    .key(key)
                    .value(value)
                    .timestamp(1703123456789L)
                    .build();

            // Create the produce request (with friend's expected format)
            ProduceRequest request = ProduceRequest.builder()
                    .topic("test-topic")
                    .partition(0)
                    .messages(List.of(message))
                    .producerId("producer-1")
                    .producerEpoch(1)
                    .requiredAcks(-1)
                    .timeoutMs(5000L)
                    .build();

            // Convert to JSON
            String json = RequestFormatter.toJson(request);

            // Print the JSON
            System.out.println("╔═══════════════════════════════════════════════════════════════╗");
            System.out.println("║  JSON Request Format (Sent to Friend's Storage Service)      ║");
            System.out.println("╚═══════════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println(json);
            System.out.println();
            
            // Pretty print
            System.out.println("╔═══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Pretty Printed JSON                                          ║");
            System.out.println("╚═══════════════════════════════════════════════════════════════╝");
            System.out.println();
            
            // Use Jackson to pretty print
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Object jsonObject = mapper.readValue(json, Object.class);
            String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
            System.out.println(prettyJson);
            
            System.out.println();
            System.out.println("✅ Note: byte[] value is automatically Base64 encoded by Jackson");
            System.out.println("   Original: \"" + valueString + "\"");
            System.out.println("   Base64:   \"" + java.util.Base64.getEncoder().encodeToString(value) + "\"");
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
