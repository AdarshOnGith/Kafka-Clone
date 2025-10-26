package com.distributedmq.storage.replication;

import com.distributedmq.common.dto.MetadataUpdateRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * consider this class as updated information, as it will be keep updated by metadata service if any changes occur
 * additioinally if some change is stale we will req metadata service for latest data upfront or in periodically after expiry time
 * Also if storage node encounter any change in knowledge like follower is down etc then i will trigger update metadata req through contrller of cluster
 * Stores metadata about partitions, leaders, followers, and ISR
 * This data is updated by the metadata service
 */
@Slf4j
public class MetadataStore {

    // topic-partition -> leader broker ID
    private final Map<String, Integer> partitionLeaders = new ConcurrentHashMap<>();

    // topic-partition -> list of follower broker IDs
    private final Map<String, List<Integer>> partitionFollowers = new ConcurrentHashMap<>();

    // topic-partition -> list of ISR broker IDs
    private final Map<String, List<Integer>> partitionISR = new ConcurrentHashMap<>();

    // topic-partition -> leader epoch
    private final Map<String, Long> partitionLeaderEpochs = new ConcurrentHashMap<>();

    // broker ID -> broker info
    private final Map<Integer, BrokerInfo> brokers = new ConcurrentHashMap<>();

    // This broker's ID (injected from config)
    private Integer localBrokerId;
    
    // This broker's host and port (from config/services.json)
    private String localBrokerHost;
    private Integer localBrokerPort;

    // Metadata service URL for notifications
    private String metadataServiceUrl;

    // RestTemplate for HTTP calls
    private final RestTemplate restTemplate = new RestTemplate();

    // Current metadata version known by this storage service
    private volatile Long currentMetadataVersion = 0L;

    // Last metadata update timestamp
    private volatile Long lastMetadataUpdateTimestamp = 0L;

    public void setLocalBrokerId(Integer brokerId) {
        this.localBrokerId = brokerId;
        log.info("Local broker ID set to: {}", brokerId);
    }

    public Integer getLocalBrokerId() {
        return localBrokerId;
    }
    
    public void setLocalBrokerHost(String host) {
        this.localBrokerHost = host;
        log.info("Local broker host set to: {}", host);
    }
    
    public void setLocalBrokerPort(Integer port) {
        this.localBrokerPort = port;
        log.info("Local broker port set to: {}", port);
    }

    public void setMetadataServiceUrl(String url) {
        this.metadataServiceUrl = url;
        log.info("Metadata service URL set to: {}", url);
    }

    public String getMetadataServiceUrl() {
        return metadataServiceUrl;
    }

    /**
     * Update metadata from metadata service (bulk update)
     * This replaces the current metadata with the new snapshot
     */
    public void updateMetadata(MetadataUpdateRequest request) {
        log.info("Updating metadata: version={}, {} brokers, {} partitions",
                request.getVersion(),
                request.getBrokers() != null ? request.getBrokers().size() : 0,
                request.getPartitions() != null ? request.getPartitions().size() : 0);

        // Update metadata version and timestamp
        if (request.getVersion() != null) {
            this.currentMetadataVersion = request.getVersion();
        }
        this.lastMetadataUpdateTimestamp = request.getTimestamp() != null ?
                request.getTimestamp() : System.currentTimeMillis();

        // Update broker information
        if (request.getBrokers() != null) {
            for (MetadataUpdateRequest.BrokerInfo brokerInfo : request.getBrokers()) {
                BrokerInfo broker = BrokerInfo.builder()
                        .id(brokerInfo.getId())
                        .host(brokerInfo.getHost())
                        .port(brokerInfo.getPort())
                        .isAlive(brokerInfo.isAlive())
                        .build();
                updateBroker(broker);
            }
        }

        // Update partition metadata
        if (request.getPartitions() != null) {
            for (MetadataUpdateRequest.PartitionMetadata partition : request.getPartitions()) {
                updatePartitionLeadership(
                        partition.getTopic(),
                        partition.getPartition(),
                        partition.getLeaderId(),
                        partition.getFollowerIds(),
                        partition.getIsrIds(),
                        partition.getLeaderEpoch()
                );
            }
        }

        log.info("Metadata update completed at timestamp: {}", request.getTimestamp());
    }

    /**
     * Update partition leadership information
     */
    public void updatePartitionLeadership(String topic, Integer partition,
                                        Integer leaderId, List<Integer> followers,
                                        List<Integer> isr, Long leaderEpoch) {
        String key = getPartitionKey(topic, partition);

        partitionLeaders.put(key, leaderId);
        partitionFollowers.put(key, new ArrayList<>(followers));
        partitionISR.put(key, new ArrayList<>(isr));
        partitionLeaderEpochs.put(key, leaderEpoch);

        log.info("Updated leadership for {}-{}: leader={}, followers={}, isr={}, epoch={}",
                topic, partition, leaderId, followers, isr, leaderEpoch);
    }

    /**
     * Check if this broker is the leader for a partition
     */
    public boolean isLeaderForPartition(String topic, Integer partition) {
        String key = getPartitionKey(topic, partition);
        Integer leaderId = partitionLeaders.get(key);
        return leaderId != null && leaderId.equals(localBrokerId);
    }

    /**
     * Check if this broker is a follower for a partition
     */
    public boolean isFollowerForPartition(String topic, Integer partition) {
        String key = getPartitionKey(topic, partition);
        List<Integer> followers = partitionFollowers.get(key);
        boolean result = followers != null && followers.contains(localBrokerId);

        log.debug("isFollowerForPartition: topic={}, partition={}, key={}, followers={}, localBrokerId={}, result={}",
                topic, partition, key, followers, localBrokerId, result);

        return result;
    }

    /**
     * Get followers for a partition
     */
    public List<BrokerInfo> getFollowersForPartition(String topic, Integer partition) {
        String key = getPartitionKey(topic, partition);
        List<Integer> followerIds = partitionFollowers.get(key);

        if (followerIds == null) {
            return Collections.emptyList();
        }

        return followerIds.stream()
                .map(brokers::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get ISR for a partition
     */
    public List<BrokerInfo> getISRForPartition(String topic, Integer partition) {
        String key = getPartitionKey(topic, partition);
        List<Integer> isrIds = partitionISR.get(key);

        if (isrIds == null) {
            return Collections.emptyList();
        }

        return isrIds.stream()
                .map(brokers::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get leader epoch for a partition
     */
    public Long getLeaderEpoch(String topic, Integer partition) {
        String key = getPartitionKey(topic, partition);
        return partitionLeaderEpochs.getOrDefault(key, 0L);
    }

    /**
     * Phase 2: ISR Lag Reporting
     * Get all partitions where this broker is a follower (not leader)
     * Used by ISRLagReporter to identify partitions to report lag for
     */
    public List<PartitionInfo> getPartitionsWhereFollower() {
        List<PartitionInfo> followerPartitions = new ArrayList<>();
        
        // Iterate through all known partitions
        for (String partitionKey : partitionFollowers.keySet()) {
            List<Integer> followers = partitionFollowers.get(partitionKey);
            
            // Check if this broker is in the followers list
            if (followers != null && followers.contains(localBrokerId)) {
                // Extract topic and partition from key
                String[] parts = partitionKey.split("-");
                if (parts.length >= 2) {
                    String topic = parts[0];
                    Integer partition = Integer.parseInt(parts[parts.length - 1]);
                    
                    // Get additional metadata
                    Integer leaderId = partitionLeaders.get(partitionKey);
                    List<Integer> isrIds = partitionISR.get(partitionKey);
                    Long leaderEpoch = partitionLeaderEpochs.getOrDefault(partitionKey, 0L);
                    
                    followerPartitions.add(new PartitionInfo(
                        topic, 
                        partition, 
                        leaderId,
                        isrIds != null ? isrIds : Collections.emptyList(),
                        leaderEpoch
                    ));
                }
            }
        }
        
        log.debug("Found {} partitions where broker {} is a follower", 
                followerPartitions.size(), localBrokerId);
        return followerPartitions;
    }

    /**
     * Update broker information
     */
    public void updateBroker(BrokerInfo broker) {
        brokers.put(broker.getId(), broker);
        log.debug("Updated broker info: {}", broker);
    }

    /**
     * Remove broker (when it goes offline) -> may trigger metadata update through conntroller of cluster
     */
    public void removeBroker(Integer brokerId) {
        brokers.remove(brokerId);
        log.info("Removed broker {}", brokerId);
    }

    /**
     * Get all known brokers
     */
    public List<BrokerInfo> getAllBrokers() {
        return new ArrayList<>(brokers.values());
    }

    /**
     * Get broker by ID
     */
    public BrokerInfo getBroker(Integer brokerId) {
        return brokers.get(brokerId);
    }

    /**
     * Generate partition key from topic and partition
     */
    private String getPartitionKey(String topic, Integer partition) {
        return topic + "-" + partition;
    }

    /**
     * Notify metadata service about partition leadership change
     * Called when this broker detects a leadership change
     */
    public void notifyPartitionLeadershipChange(String topic, Integer partition,
                                              Integer newLeaderId, List<Integer> followers,
                                              List<Integer> isr, Long leaderEpoch) {
        if (metadataServiceUrl == null) {
            log.warn("Metadata service URL not configured, cannot notify about leadership change");
            return;
        }

        try {
            // Create metadata update request
            MetadataUpdateRequest.PartitionMetadata partitionMetadata =
                    new MetadataUpdateRequest.PartitionMetadata();
            partitionMetadata.setTopic(topic);
            partitionMetadata.setPartition(partition);
            partitionMetadata.setLeaderId(newLeaderId);
            partitionMetadata.setFollowerIds(followers);
            partitionMetadata.setIsrIds(isr);
            partitionMetadata.setLeaderEpoch(leaderEpoch);

            MetadataUpdateRequest updateRequest = MetadataUpdateRequest.builder()
                    .partitions(List.of(partitionMetadata))
                    .timestamp(System.currentTimeMillis())
                    .build();

            // Send notification to metadata service
            notifyMetadataService(updateRequest);

            log.info("Notified metadata service about partition leadership change for {}-{}",
                    topic, partition);

        } catch (Exception e) {
            log.error("Failed to notify metadata service about partition leadership change: {}", e.getMessage());
        }
    }

    /**
     * Notify metadata service about broker status change
     * Called when this broker detects another broker going offline/online
     */
    public void notifyBrokerStatusChange(Integer brokerId, boolean isAlive) {
        if (metadataServiceUrl == null) {
            log.warn("Metadata service URL not configured, cannot notify about broker status change");
            return;
        }

        try {
            // Create broker info
            MetadataUpdateRequest.BrokerInfo brokerInfo =
                    new MetadataUpdateRequest.BrokerInfo();
            brokerInfo.setId(brokerId);
            brokerInfo.setAlive(isAlive);

            MetadataUpdateRequest updateRequest = MetadataUpdateRequest.builder()
                    .brokers(List.of(brokerInfo))
                    .timestamp(System.currentTimeMillis())
                    .build();

            // Send notification to metadata service
            notifyMetadataService(updateRequest);

            log.info("Notified metadata service about broker {} status change: {}",
                    brokerId, isAlive ? "online" : "offline");

        } catch (Exception e) {
            log.error("Failed to notify metadata service about broker status change: {}", e.getMessage());
        }
    }

    /**
     * Send notification to metadata service
     */
    private void notifyMetadataService(MetadataUpdateRequest updateRequest) {
        try {
            String url = metadataServiceUrl + "/storage-updates";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<MetadataUpdateRequest> requestEntity = new HttpEntity<>(updateRequest, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully notified metadata service about update");
            } else {
                log.warn("Metadata service returned non-success status: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to notify metadata service: {}", e.getMessage());
        }
    }

    /**
     * Register this broker with the metadata service
     */
    public void registerWithMetadataService() {
        if (metadataServiceUrl == null) {
            log.warn("Metadata service URL not configured, cannot register broker");
            return;
        }

        try {
            // Create registration request using actual broker configuration
            Map<String, Object> registration = new HashMap<>();
            registration.put("id", localBrokerId);
            registration.put("host", localBrokerHost != null ? localBrokerHost : "localhost");
            registration.put("port", localBrokerPort != null ? localBrokerPort : 8081);
            registration.put("rack", "default");

            log.info("Registering broker {} at {}:{} with metadata service at {}",
                    localBrokerId, 
                    registration.get("host"), 
                    registration.get("port"),
                    metadataServiceUrl);

            // Send registration request
            String endpoint = metadataServiceUrl + "/api/v1/metadata/brokers";
            Map<String, Object> response = restTemplate.postForObject(endpoint, registration, Map.class);

            if (response != null) {
                log.info("Successfully registered broker {} with metadata service: {}", localBrokerId, response);
            } else {
                log.warn("Broker registration returned null response");
            }

        } catch (Exception e) {
            log.error("Failed to register broker with metadata service: {}", e.getMessage());
        }
    }

    /**
     * Get current metadata version
     */
    public Long getCurrentMetadataVersion() {
        return currentMetadataVersion;
    }

    /**
     * Get last metadata update timestamp
     */
    public Long getLastMetadataUpdateTimestamp() {
        return lastMetadataUpdateTimestamp;
    }

    // TODO: Add methods to sync with metadata service (pull model - request metadata)
    // These will be implemented when metadata service is available
    // - requestMetadataRefresh() - Request latest metadata from metadata service
    // - registerWithMetadataService() - Register this broker with metadata service

    // TODO: Add methods to sync with metadata service (for later implementation)
    // - fetchPartitionMetadata()
    // - registerBroker()
    // - updateISR() - ISR management for advanced replication

    // TODO: Add initialization methods for testing/basic setup (needed for replication flow)
    // - initializeTestMetadata() - populate sample brokers and partition leadership
    // - loadFromConfig() - load static metadata from configuration

    /**
     * Phase 2: ISR Lag Reporting
     * Simple partition info holder for lag reporting
     */
    public static class PartitionInfo {
        private final String topic;
        private final Integer partition;
        private final Integer leaderId;
        private final List<Integer> isrIds;
        private final Long leaderEpoch;
        
        public PartitionInfo(String topic, Integer partition, Integer leaderId, 
                           List<Integer> isrIds, Long leaderEpoch) {
            this.topic = topic;
            this.partition = partition;
            this.leaderId = leaderId;
            this.isrIds = isrIds;
            this.leaderEpoch = leaderEpoch;
        }
        
        public String getTopic() { return topic; }
        public Integer getPartition() { return partition; }
        public Integer getLeaderId() { return leaderId; }
        public List<Integer> getIsrIds() { return isrIds; }
        public Long getLeaderEpoch() { return leaderEpoch; }
    }
}

