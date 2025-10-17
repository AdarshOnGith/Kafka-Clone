package com.distributedmq.client.producer;

import lombok.Builder;
import lombok.Data;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration for Producer
 */
@Data
@Builder
public class ProducerConfig {
    
    // Metadata service endpoints
    private String metadataServiceUrl;
    
    // Storage service endpoints (optional, can be discovered)
    private String storageServiceUrl;
    
    // Producer settings
    @Builder.Default
    private Integer maxInFlightRequests = 5;
    
    @Builder.Default
    private Integer requiredAcks = 1; // 0, 1, or -1
    
    @Builder.Default
    private Long requestTimeoutMs = 30000L;
    
    @Builder.Default
    private Integer retries = 3;
    
    @Builder.Default
    private String compressionType = "none";
    
    @Builder.Default
    private Integer maxBlockMs = 60000;

    /**
     * Load configuration from properties file
     * 
     * @param filePath Path to producer-config.properties file
     * @return ProducerConfig instance
     */
    public static ProducerConfig loadFromFile(String filePath) {
        Properties props = new Properties();
        
        try (InputStream input = new FileInputStream(filePath)) {
            props.load(input);
            
            return ProducerConfig.builder()
                    .metadataServiceUrl(props.getProperty("metadata.service.url", "http://localhost:8081"))
                    .requestTimeoutMs(Long.parseLong(props.getProperty("producer.request.timeout.ms", "30000")))
                    .build();
                    
        } catch (IOException e) {
            throw new RuntimeException("Failed to load producer configuration from: " + filePath, e);
        }
    }
    
    /**
     * Load configuration from classpath resource
     * 
     * @param resourceName Resource name (e.g., "producer-config.properties")
     * @return ProducerConfig instance
     */
    public static ProducerConfig loadFromResource(String resourceName) {
        Properties props = new Properties();
        
        try (InputStream input = ProducerConfig.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new RuntimeException("Unable to find resource: " + resourceName);
            }
            
            props.load(input);
            
            return ProducerConfig.builder()
                    .metadataServiceUrl(props.getProperty("metadata.service.url", "http://localhost:8081"))
                    .requestTimeoutMs(Long.parseLong(props.getProperty("producer.request.timeout.ms", "30000")))
                    .build();
                    
        } catch (IOException e) {
            throw new RuntimeException("Failed to load producer configuration from resource: " + resourceName, e);
        }
    }
}
