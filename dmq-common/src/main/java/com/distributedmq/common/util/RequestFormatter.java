package com.distributedmq.common.util;

import com.distributedmq.common.model.TopicMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for formatting requests to DMQ services
 * Supports metadata and storage operations
 */
public class RequestFormatter {

    private static final String METADATA_ENDPOINT = "/api/v1/metadata/topics";
    private static final String STORAGE_ENDPOINT = "/api/v1/storage/messages";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Build URL for metadata request
     *
     * @param baseUrl Base URL of metadata service (e.g., http://localhost:8081)
     * @param topicName Topic name
     * @return Full URL
     */
    public static String buildMetadataRequestUrl(String baseUrl, String topicName) {
        return baseUrl + METADATA_ENDPOINT + "/" + topicName;
    }

    /**
     * Build URL for storage message request
     *
     * @param brokerUrl Broker URL (e.g., http://localhost:8082)
     * @return Full URL for message sending
     */
    public static String buildStorageRequestUrl(String brokerUrl) {
        return brokerUrl + STORAGE_ENDPOINT;
    }

    /**
     * Parse JSON response to TopicMetadata
     */
    public static TopicMetadata parseMetadataResponse(String jsonResponse) {
        try {
            return objectMapper.readValue(jsonResponse, TopicMetadata.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse metadata response", e);
        }
    }

    /**
     * Serialize object to JSON string
     */
    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Deserialize JSON string to object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON to object", e);
        }
    }
}
