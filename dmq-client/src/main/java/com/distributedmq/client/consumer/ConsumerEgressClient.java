package com.distributedmq.client.consumer;

import com.distributedmq.common.dto.ConsumerSubscriptionRequest;
import com.distributedmq.common.dto.ConsumerSubscriptionResponse;
import com.distributedmq.common.dto.ConsumeRequest;
import com.distributedmq.common.dto.ConsumeResponse;
import com.distributedmq.common.model.ConsumerOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for communicating with Consumer Egress Service
 * Handles all network communication between consumer library and Kafka-side services
 */
@Slf4j
public class ConsumerEgressClient {

    private final String egressServiceUrl;
    private final RestTemplate restTemplate;

    public ConsumerEgressClient(String metadataServiceUrl) {
        this.egressServiceUrl = metadataServiceUrl;
        this.restTemplate = new RestTemplate();
        
        log.info("ConsumerEgressClient initialized with egress service URL: {}", egressServiceUrl);
    }

    /**
     * Join consumer group and get partition metadata
     * Phase 1: Simple join, get all partition metadata
     * 
     * CES will:
     * 1. Create group if not exists
     * 2. Add consumer to group
     * 3. Return partition metadata (leader, currentOffset, highWaterMark, ISR)
     */
    public ConsumerSubscriptionResponse joinGroup(ConsumerSubscriptionRequest request) {
        String url = egressServiceUrl + "/api/consumer/join-group";
        
        log.info("Joining consumer group: group={}, consumerId={}, topics={}", 
            request.getGroupId(), request.getConsumerId(), request.getTopics());
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<ConsumerSubscriptionRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<ConsumerSubscriptionResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                ConsumerSubscriptionResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ConsumerSubscriptionResponse subscriptionResponse = response.getBody();
                
                if (subscriptionResponse.isSuccess()) {
                    log.info("Successfully joined group: group={}, partitions={}", 
                        subscriptionResponse.getGroupId(),
                        subscriptionResponse.getPartitions().size());
                    
                    // Log each partition metadata
                    subscriptionResponse.getPartitions().forEach(partition -> 
                        log.debug("Partition metadata: {}", partition));
                        
                    return subscriptionResponse;
                } else {
                    log.error("Join group failed: {}", subscriptionResponse.getErrorMessage());
                    return subscriptionResponse;
                }
            } else {
                log.error("Unexpected response status: {}", response.getStatusCode());
                return ConsumerSubscriptionResponse.builder()
                    .success(false)
                    .errorMessage("Unexpected response status: " + response.getStatusCode())
                    .build();
            }
            
        } catch (RestClientException e) {
            log.error("Failed to join group: {}", e.getMessage(), e);
            return ConsumerSubscriptionResponse.builder()
                .success(false)
                .errorMessage("Network error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Fetch messages from storage service for a specific partition
     */
    public ConsumeResponse fetchMessages(ConsumeRequest request, String leaderBrokerUrl) {
        String url = leaderBrokerUrl + "/api/storage/fetch";
        
        log.debug("Fetching messages from {}: topic={}, partition={}, offset={}", 
            url, request.getTopic(), request.getPartition(), request.getOffset());
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<ConsumeRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<ConsumeResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                ConsumeResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                log.error("Unexpected response status: {}", response.getStatusCode());
                return ConsumeResponse.builder()
                    .success(false)
                    .errorMessage("Unexpected response status: " + response.getStatusCode())
                    .build();
            }
            
        } catch (RestClientException e) {
            log.error("Failed to fetch messages: {}", e.getMessage(), e);
            return ConsumeResponse.builder()
                .success(false)
                .errorMessage("Network error: " + e.getMessage())
                .build();
        }
    }

    // ============================================================================
    // FUTURE METHODS (Phase 2+): Keep for multi-member groups and offset commit
    // ============================================================================

    /**
     * Commit consumer offsets to metadata service
     * TODO: Implement in Phase 2
     */
    public boolean commitOffsets(String consumerGroup, List<ConsumerOffset> offsets) {
        String url = egressServiceUrl + "/api/consumer/commit";
        
        log.debug("Committing offsets for group {}: {} partitions", consumerGroup, offsets.size());
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> request = new HashMap<>();
            request.put("consumerGroup", consumerGroup);
            request.put("offsets", offsets);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>)(ResponseEntity<?>)restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (RestClientException e) {
            log.error("Failed to commit offsets: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send heartbeat to maintain consumer group membership
     * TODO: Implement in Phase 2
     */
    public boolean sendHeartbeat(String consumerGroup, String consumerId, Integer generationId) {
        String url = egressServiceUrl + "/api/consumer/heartbeat";
        
        log.trace("Sending heartbeat for consumer {} in group {}", consumerId, consumerGroup);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> request = new HashMap<>();
            request.put("consumerGroup", consumerGroup);
            request.put("consumerId", consumerId);
            request.put("generationId", generationId);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>)(ResponseEntity<?>)restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (RestClientException e) {
            log.error("Failed to send heartbeat: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Leave consumer group (called on close)
     * TODO: Implement in Phase 2
     */
    public boolean leaveGroup(String consumerGroup, String consumerId) {
        String url = egressServiceUrl + "/api/consumer/leave";
        
        log.info("Leaving consumer group: group={}, consumerId={}", consumerGroup, consumerId);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> request = new HashMap<>();
            request.put("consumerGroup", consumerGroup);
            request.put("consumerId", consumerId);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>)(ResponseEntity<?>)restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (RestClientException e) {
            log.error("Failed to leave group: {}", e.getMessage(), e);
            return false;
        }
    }
}


    /**
     * Fetch messages from storage service for a specific partition
     */
    public ConsumeResponse fetchMessages(ConsumeRequest request, String leaderBrokerUrl) {
        String url = leaderBrokerUrl + "/api/storage/fetch";
        
        log.debug("Fetching messages from {}: topic={}, partition={}, offset={}", 
            url, request.getTopic(), request.getPartition(), request.getOffset());
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<ConsumeRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<ConsumeResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                ConsumeResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                log.error("Unexpected response status: {}", response.getStatusCode());
                return ConsumeResponse.builder()
                    .success(false)
                    .errorMessage("Unexpected response status: " + response.getStatusCode())
                    .build();
            }
            
        } catch (RestClientException e) {
            log.error("Failed to fetch messages: {}", e.getMessage(), e);
            return ConsumeResponse.builder()
                .success(false)
                .errorMessage("Network error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Commit consumer offsets to metadata service
     */
    public boolean commitOffsets(String consumerGroup, List<ConsumerOffset> offsets) {
        String url = egressServiceUrl + "/api/consumer/commit";
        
        log.debug("Committing offsets for group {}: {} partitions", consumerGroup, offsets.size());
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> request = new HashMap<>();
            request.put("consumerGroup", consumerGroup);
            request.put("offsets", offsets);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>)(ResponseEntity<?>)restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (RestClientException e) {
            log.error("Failed to commit offsets: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send heartbeat to maintain consumer group membership
     */
    public boolean sendHeartbeat(String consumerGroup, String consumerId, Integer generationId) {
        String url = egressServiceUrl + "/api/consumer/heartbeat";
        
        log.trace("Sending heartbeat for consumer {} in group {}", consumerId, consumerGroup);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> request = new HashMap<>();
            request.put("consumerGroup", consumerGroup);
            request.put("consumerId", consumerId);
            request.put("generationId", generationId);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>)(ResponseEntity<?>)restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (RestClientException e) {
            log.error("Failed to send heartbeat: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Leave consumer group (called on close)
     */
    public boolean leaveGroup(String consumerGroup, String consumerId) {
        String url = egressServiceUrl + "/api/consumer/leave";
        
        log.info("Leaving consumer group: group={}, consumerId={}", consumerGroup, consumerId);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> request = new HashMap<>();
            request.put("consumerGroup", consumerGroup);
            request.put("consumerId", consumerId);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>)(ResponseEntity<?>)restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (RestClientException e) {
            log.error("Failed to leave group: {}", e.getMessage(), e);
            return false;
        }
    }
}
