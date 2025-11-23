package com.distributedmq.client.cli.utils;

import com.distributedmq.common.config.ServiceDiscovery;
import com.distributedmq.common.dto.ConsumerGroupResponse;
import com.distributedmq.common.dto.CreateTopicRequest;
import com.distributedmq.common.model.TopicMetadata;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Client for interacting with metadata service
 */
public class MetadataServiceClient {
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String controllerUrl;
    
    public MetadataServiceClient(String metadataUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        
        if (metadataUrl != null) {
            this.controllerUrl = metadataUrl;
        } else {
            try {
                this.controllerUrl = discoverController();
            } catch (Exception e) {
                throw new RuntimeException("Failed to discover controller", e);
            }
        }
    }
    
    /**
     * Discover active controller from configured metadata services
     */
    public String discoverController() throws Exception {
        ServiceDiscovery.loadConfig();
        List<ServiceDiscovery.ServiceInfo> metadataServices = ServiceDiscovery.getAllMetadataServices();
        
        for (ServiceDiscovery.ServiceInfo service : metadataServices) {
            try {
                String url = service.getUrl() + "/api/v1/metadata/controller";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    // Parse response to get actual controller URL
                    try {
                        var controllerInfo = objectMapper.readTree(response.body());
                        String actualControllerUrl = controllerInfo.get("controllerUrl").asText();
                        System.out.println("📡 Connected to controller: " + actualControllerUrl);
                        return actualControllerUrl;
                    } catch (Exception e) {
                        System.out.println("📡 Connected to controller: " + service.getUrl());
                        return service.getUrl();
                    }
                }
            } catch (Exception e) {
                // Try next service
            }
        }
        
        throw new RuntimeException("Cannot connect to any metadata service. Please check if services are running.");
    }
    
    /**
     * Create a new topic
     */
    public void createTopic(CreateTopicRequest request) throws Exception {
        String url = controllerUrl + "/api/v1/metadata/topics";
        String requestBody = objectMapper.writeValueAsString(request);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        // Handle 503 - Not the leader, retry with actual leader
        if (response.statusCode() == 503) {
            String leaderHeader = response.headers().firstValue("X-Controller-Leader").orElse(null);
            if (leaderHeader != null) {
                // Re-discover controller and retry
                System.out.println("⚠️  Node is not the leader. Discovering actual leader...");
                this.controllerUrl = discoverController();
                // Retry the request
                createTopic(request);
                return;
            }
        }
        
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Failed to create topic: HTTP " + response.statusCode() + " - " + response.body());
        }
    }
    
    /**
     * Get topic metadata
     */
    public TopicMetadata getTopicMetadata(String topicName) throws Exception {
        String url = controllerUrl + "/api/v1/metadata/topics/" + topicName;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 404) {
            throw new RuntimeException("Topic '" + topicName + "' not found. Create it first using: mycli create-topic --name " + topicName);
        }
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get topic metadata: HTTP " + response.statusCode());
        }
        
        return objectMapper.readValue(response.body(), TopicMetadata.class);
    }
    
    /**
     * List all topics
     */
    public List<String> listTopics() throws Exception {
        String url = controllerUrl + "/api/v1/metadata/topics";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to list topics: HTTP " + response.statusCode());
        }
        
        return objectMapper.readValue(response.body(), new TypeReference<List<String>>() {});
    }
    
    /**
     * List all consumer groups
     */
    public List<ConsumerGroupResponse> listConsumerGroups() throws Exception {
        String url = controllerUrl + "/api/v1/metadata/consumer-groups";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to list consumer groups: HTTP " + response.statusCode());
        }
        
        return objectMapper.readValue(response.body(), new TypeReference<List<ConsumerGroupResponse>>() {});
    }
    
    /**
     * Describe a specific consumer group
     */
    public ConsumerGroupResponse describeConsumerGroup(String groupId) throws Exception {
        String url = controllerUrl + "/api/v1/metadata/consumer-groups/" + groupId;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 404) {
            return null;
        }
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to describe consumer group: HTTP " + response.statusCode());
        }
        
        return objectMapper.readValue(response.body(), ConsumerGroupResponse.class);
    }
}
