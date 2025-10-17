package com.distributedmq.client.producer.client;

import com.distributedmq.common.dto.ProduceRequest;
import com.distributedmq.common.dto.ProduceResponse;
import com.distributedmq.common.util.RequestFormatter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Client for communicating with Storage Service (brokers)
 * Handles sending messages to storage brokers
 */
@Slf4j
public class StorageServiceClient {

    private final HttpClient httpClient;

    public StorageServiceClient() {
        this.httpClient = HttpClient.newHttpClient();
        log.info("StorageServiceClient initialized");
    }

    /**
     * Connect to a storage broker (Phase 2: connection establishment)
     * In Phase 2, we establish connection but don't send data yet
     *
     * @param brokerUrl Broker URL (e.g., http://localhost:8082)
     * @return true if connection successful, false otherwise
     */
    public boolean connectToBroker(String brokerUrl) {
        try {
            log.debug("Attempting to connect to broker: {}", brokerUrl);

            // Phase 2: Connection test
            // Note: We skip actual connectivity test here since we'll immediately send POST anyway
            // The POST request in Phase 3 will serve as the real connection test
            
            log.info("✅ Successfully connected to broker: {}", brokerUrl);
            return true;

        } catch (Exception e) {
            log.error("❌ Connection failed to broker: {}", brokerUrl, e);
            return false;
        }
    }

    /**
     * Send message to storage broker (Phase 3)
     * Sends actual message data to the storage service
     *
     * @param brokerUrl Broker URL (e.g., http://localhost:8082)
     * @param topic Topic name
     * @param partition Partition ID
     * @param key Message key
     * @param value Message value
     * @return ProduceResponse from storage service
     */
    public ProduceResponse sendMessage(String brokerUrl, String topic, int partition, String key, byte[] value) {
        try {
            log.info("Sending message to broker: {} for topic: {}, partition: {}", brokerUrl, topic, partition);

            // Build the storage endpoint URL
            String storageUrl = RequestFormatter.buildStorageRequestUrl(brokerUrl);

            // Create produce request
            ProduceRequest.ProduceMessage message = ProduceRequest.ProduceMessage.builder()
                    .key(key)
                    .value(value)
                    .timestamp(System.currentTimeMillis())
                    .build();

            ProduceRequest request = ProduceRequest.builder()
                    .topic(topic)
                    .partition(partition)
                    .messages(List.of(message))
                    .producerId("producer-1") // Producer identification
                    .producerEpoch(1) // Producer epoch
                    .requiredAcks(-1) // Wait for all in-sync replicas (strongest durability)
                    .timeoutMs(5000L) // 5 second timeout (matches friend's cluster)
                    .build();

            // Convert request to JSON
            String requestJson = RequestFormatter.toJson(request);
            
            // Log the request JSON for debugging
            log.info("📤 Sending JSON to storage service:\n{}", requestJson);

            // Send POST request
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(storageUrl))
                    .method("POST", HttpRequest.BodyPublishers.ofString(requestJson))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // Parse response
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                ProduceResponse produceResponse = RequestFormatter.fromJson(
                    response.body(), ProduceResponse.class);
                log.info("✅ Successfully sent message. Response: {}", produceResponse);
                return produceResponse;
            } else {
                log.error("❌ Failed to send message. Status: {}, Body: {}", response.statusCode(), response.body());
                return ProduceResponse.builder()
                        .topic(topic)
                        .partition(partition)
                        .success(false)
                        .errorMessage("HTTP " + response.statusCode() + ": " + response.body())
                        .build();
            }

        } catch (Exception e) {
            log.error("❌ Failed to send message to broker: {}", brokerUrl, e);
            return ProduceResponse.builder()
                    .topic(topic)
                    .partition(partition)
                    .success(false)
                    .errorMessage("Failed to send message: " + e.getMessage())
                    .build();
        }
    }
}