package com.distributedmq.client.producer.client;

import com.distributedmq.common.model.TopicMetadata;
import com.distributedmq.common.util.RequestFormatter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Client for communicating with Metadata Service
 * Handles fetching topic metadata (partition count, leaders, etc.)
 */
@Slf4j
public class MetadataServiceClient {
    
    private final String metadataServiceUrl;
    private final HttpClient httpClient;
    
    public MetadataServiceClient(String metadataServiceUrl) {
        this.metadataServiceUrl = metadataServiceUrl;
        this.httpClient = HttpClient.newHttpClient();
        
        log.info("MetadataServiceClient initialized with URL: {}", metadataServiceUrl);
    }
    
    /**
     * Fetch topic metadata from metadata service
     * 
     * @param topicName Topic name
     * @return TopicMetadata containing partition info and leaders
     */
    public TopicMetadata getTopicMetadata(String topicName) {
        log.debug("Fetching metadata for topic: {}", topicName);
        
        try {
            // Step 1: Build request URL using RequestFormatter
            String requestUrl = RequestFormatter.buildMetadataRequestUrl(metadataServiceUrl, topicName);
            log.debug("Request URL: {}", requestUrl);
            
            // Step 2: Build HTTP GET request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            // Step 3: Send request and get response
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            log.debug("Response status: {}", response.statusCode());
            
            // Step 4: Check response status
            if (response.statusCode() != 200) {
                throw new RuntimeException(String.format(
                        "Failed to fetch metadata for topic '%s'. Status: %d, Response: %s",
                        topicName, response.statusCode(), response.body()));
            }
            
            // Step 5: Parse response using RequestFormatter
            TopicMetadata metadata = RequestFormatter.parseMetadataResponse(response.body());
            
            log.info("✅ Successfully fetched metadata for topic '{}': {} partitions", 
                    topicName, metadata.getPartitionCount());
            
            return metadata;
            
        } catch (Exception e) {
            log.error("❌ Error fetching metadata for topic: {}", topicName, e);
            throw new RuntimeException("Failed to fetch metadata for topic: " + topicName, e);
        }
    }
}
