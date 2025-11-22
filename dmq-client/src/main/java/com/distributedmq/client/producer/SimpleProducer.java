package com.distributedmq.client.producer;

import com.distributedmq.client.cli.utils.MetadataServiceClient;
import com.distributedmq.common.dto.ProduceRequest;
import com.distributedmq.common.dto.ProduceResponse;
import com.distributedmq.common.model.TopicMetadata;
import com.distributedmq.common.model.PartitionMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple producer for CLI usage
 * Handles metadata discovery, partition selection, and message sending
 */
public class SimpleProducer {
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final MetadataServiceClient metadataClient;
    private final Map<String, TopicMetadata> metadataCache;
    private final AtomicInteger roundRobinCounter;
    
    public SimpleProducer(String metadataUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.metadataClient = new MetadataServiceClient(metadataUrl);
        this.metadataCache = new HashMap<>();
        this.roundRobinCounter = new AtomicInteger(0);
    }
    
    /**
     * Send a message to a topic
     */
    public ProduceResponse send(String topic, String key, String value, Integer partition, int acks) throws Exception {
        // Get topic metadata
        TopicMetadata metadata = getTopicMetadata(topic);
        
        // Select partition
        int targetPartition = selectPartition(key, partition, metadata.getPartitions().size());
        
        // Find leader broker for partition
        PartitionMetadata partitionMeta = metadata.getPartitions().stream()
                .filter(p -> p.getPartitionId() == targetPartition)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Partition " + targetPartition + " not found"));
        
        if (partitionMeta.getLeader() == null) {
            throw new RuntimeException("No leader found for partition " + targetPartition);
        }
        
        String leaderUrl = partitionMeta.getLeader().getHost() + ":" + partitionMeta.getLeader().getPort();
        
        // Create produce message with Base64-encoded value (as string for JSON serialization)
        String base64Value = java.util.Base64.getEncoder().encodeToString(value.getBytes());
        
        // Create a custom message map to ensure proper JSON serialization
        java.util.Map<String, Object> messageMap = new java.util.HashMap<>();
        messageMap.put("key", key);
        messageMap.put("value", base64Value);  // Send as Base64 string, not byte array
        // Don't send timestamp - let server generate it
        
        // Create request map
        java.util.Map<String, Object> requestMap = new java.util.HashMap<>();
        requestMap.put("topic", topic);
        requestMap.put("partition", targetPartition);
        requestMap.put("messages", java.util.List.of(messageMap));
        requestMap.put("producerId", "cli-producer");
        requestMap.put("producerEpoch", 0);
        requestMap.put("requiredAcks", acks);
        requestMap.put("timeoutMs", 30000L);
        
        // Send to broker
        return sendToBroker(leaderUrl, requestMap);
    }
    
    /**
     * Get topic metadata (with caching)
     */
    private TopicMetadata getTopicMetadata(String topic) throws Exception {
        // Simple caching - no TTL for CLI usage
        if (metadataCache.containsKey(topic)) {
            return metadataCache.get(topic);
        }
        
        TopicMetadata metadata = metadataClient.getTopicMetadata(topic);
        metadataCache.put(topic, metadata);
        return metadata;
    }
    
    /**
     * Select partition based on key or explicit partition
     */
    private int selectPartition(String key, Integer explicitPartition, int partitionCount) {
        if (explicitPartition != null) {
            if (explicitPartition < 0 || explicitPartition >= partitionCount) {
                throw new IllegalArgumentException("Invalid partition " + explicitPartition + 
                        ". Valid range: 0-" + (partitionCount - 1));
            }
            return explicitPartition;
        }
        
        if (key != null) {
            // Hash-based partitioning
            return Math.abs(key.hashCode()) % partitionCount;
        }
        
        // Round-robin
        return roundRobinCounter.getAndIncrement() % partitionCount;
    }
    
    /**
     * Send produce request to broker
     */
    private ProduceResponse sendToBroker(String brokerUrl, java.util.Map<String, Object> requestMap) throws Exception {
        String url = "http://" + brokerUrl + "/api/v1/storage/messages";
        String requestBody = objectMapper.writeValueAsString(requestMap);
        
        // Debug: print the actual JSON
        System.out.println("DEBUG - Sending to: " + url);
        System.out.println("DEBUG - Request body: " + requestBody);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            ProduceResponse errorResponse = new ProduceResponse();
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage("HTTP " + response.statusCode() + ": " + response.body());
            return errorResponse;
        }
        
        return objectMapper.readValue(response.body(), ProduceResponse.class);
    }
    
    /**
     * Close producer and release resources
     */
    public void close() {
        // HttpClient doesn't need explicit closing in Java 11+
        metadataCache.clear();
    }
}
