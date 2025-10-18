package com.distributedmq.metadata.service;

import com.distributedmq.common.dto.HeartbeatRequest;
import com.distributedmq.common.dto.HeartbeatResponse;
import com.distributedmq.common.dto.MetadataUpdateRequest;
import com.distributedmq.common.dto.MetadataUpdateResponse;
import com.distributedmq.common.model.TopicConfig;
import com.distributedmq.common.model.TopicMetadata;
import com.distributedmq.common.model.BrokerNode;
import com.distributedmq.common.model.BrokerStatus;
import com.distributedmq.metadata.dto.CreateTopicRequest;
import com.distributedmq.metadata.entity.TopicEntity;
import com.distributedmq.metadata.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of MetadataService
 * Business logic layer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataServiceImpl implements MetadataService {

    private final TopicRepository topicRepository;
    private final ControllerService controllerService;
    private final MetadataPushService metadataPushService;
    private final com.distributedmq.metadata.coordination.RaftController raftController;
    private final RestTemplate restTemplate;

    // In-memory storage for non-active metadata services
    private final Map<String, TopicMetadata> metadataCache = new ConcurrentHashMap<>();
    private volatile boolean hasSyncedData = false;

    // Metadata service registry (controller only)
    private final Map<Integer, MetadataServiceInfo> metadataServiceRegistry = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("Initializing MetadataServiceImpl");
        // Register this service with itself if it's the controller
        if (raftController.isControllerLeader()) {
            registerMetadataService(getCurrentServiceId(), System.currentTimeMillis());
        }
    }

    @Override
    @Transactional
    public TopicMetadata createTopic(CreateTopicRequest request) {
        log.info("Creating topic: {} with {} partitions, replication factor: {}",
                request.getTopicName(), request.getPartitionCount(), request.getReplicationFactor());

        // Only active controller can create topics
        if (!raftController.isControllerLeader()) {
            throw new IllegalStateException("Only active controller can create topics. Current leader: " +
                    raftController.getControllerLeaderId());
        }

        // Check if topic already exists
        Optional<TopicEntity> existingTopic = topicRepository.findByTopicName(request.getTopicName());
        if (existingTopic.isPresent()) {
            throw new IllegalArgumentException("Topic already exists: " + request.getTopicName());
        }

        // Create topic configuration with defaults
        TopicConfig config = TopicConfig.builder()
                .retentionMs(request.getRetentionMs() != null ? request.getRetentionMs() : 604800000L) // 7 days
                .retentionBytes(request.getRetentionBytes() != null ? request.getRetentionBytes() : -1L) // unlimited
                .segmentBytes(request.getSegmentBytes() != null ? request.getSegmentBytes() : 1073741824) // 1GB
                .compressionType(request.getCompressionType() != null ? request.getCompressionType() : "none")
                .minInsyncReplicas(request.getMinInsyncReplicas() != null ? request.getMinInsyncReplicas() : 1)
                .build();

        // Create topic metadata
        TopicMetadata metadata = TopicMetadata.builder()
                .topicName(request.getTopicName())
                .partitionCount(request.getPartitionCount())
                .replicationFactor(request.getReplicationFactor())
                .createdAt(System.currentTimeMillis())
                .config(config)
                .build();

        // Assign partitions to brokers via controller service
        metadata.setPartitions(controllerService.assignPartitions(
                request.getTopicName(),
                request.getPartitionCount(),
                request.getReplicationFactor()
        ));

        // Persist to database
        TopicEntity entity = TopicEntity.fromMetadata(metadata);
        TopicEntity savedEntity = topicRepository.save(entity);

        log.info("Successfully created topic: {} with {} partitions", request.getTopicName(), request.getPartitionCount());

        // TODO: Notify storage nodes to create partition directories
        // This will be implemented when storage service metadata sync is added

        // Push metadata to all nodes
        metadataPushService.pushTopicMetadata(savedEntity.toMetadata());

        return savedEntity.toMetadata();
    }

    @Override
    public TopicMetadata getTopicMetadata(String topicName) {
        log.debug("Getting metadata for topic: {}", topicName);

        // If this is not the active controller, read from cache
        if (!raftController.isControllerLeader()) {
            if (!hasSyncedData) {
                log.debug("Non-active controller requested topic metadata but no synced data available. Triggering sync...");
                synchronizeMetadataIfNeeded();
                // After sync, data should be available in cache
            }

            TopicMetadata metadata = metadataCache.get(topicName);
            if (metadata == null) {
                throw new IllegalArgumentException("Topic not found: " + topicName);
            }
            return metadata;
        }

        // Active controller: read from database
        Optional<TopicEntity> entity = topicRepository.findByTopicName(topicName);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("Topic not found: " + topicName);
        }

        TopicMetadata metadata = entity.get().toMetadata();

        // Get current partition assignments from controller
        metadata.setPartitions(controllerService.assignPartitions(
                topicName,
                metadata.getPartitionCount(),
                metadata.getReplicationFactor()
        ));

        return metadata;
    }

    @Override
    public List<String> listTopics() {
        log.debug("Listing all topics");

        // If this is not the active controller, read from cache
        if (!raftController.isControllerLeader()) {
            if (!hasSyncedData) {
                log.debug("Non-active controller requested topic list but no synced data available. Triggering sync...");
                synchronizeMetadataIfNeeded();
                // After sync, data should be available in cache
            }

            return List.copyOf(metadataCache.keySet());
        }

        // Active controller: read from database
        return topicRepository.findAll().stream()
                .map(TopicEntity::getTopicName)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteTopic(String topicName) {
        log.info("Deleting topic: {}", topicName);

        // Only active controller can delete topics
        if (!raftController.isControllerLeader()) {
            throw new IllegalStateException("Only active controller can delete topics. Current leader: " +
                    raftController.getControllerLeaderId());
        }

        Optional<TopicEntity> entity = topicRepository.findByTopicName(topicName);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("Topic not found: " + topicName);
        }

        // Cleanup partitions via controller service
        controllerService.cleanupTopicPartitions(topicName);

        // Delete from repository
        topicRepository.delete(entity.get());

        log.info("Successfully deleted topic: {}", topicName);

        // TODO: Notify storage nodes to delete data
        // This will be implemented when storage service metadata sync is added

        // Push cluster metadata update (without the deleted topic) to storage nodes
        try {
            metadataPushService.pushFullClusterMetadata();
            log.info("Successfully pushed cluster metadata update after deleting topic {}", topicName);
        } catch (Exception e) {
            log.error("Failed to push cluster metadata update after deleting topic {}: {}", topicName, e.getMessage());
            // Don't fail the deletion if push fails
        }
    }

    @Override
    @Transactional
    public void updateTopicMetadata(TopicMetadata metadata) {
        log.debug("Updating metadata for topic: {}", metadata.getTopicName());

        // Only active controller can update topics
        if (!raftController.isControllerLeader()) {
            throw new IllegalStateException("Only active controller can update topics. Current leader: " +
                    raftController.getControllerLeaderId());
        }

        Optional<TopicEntity> existingEntity = topicRepository.findByTopicName(metadata.getTopicName());
        if (existingEntity.isEmpty()) {
            throw new IllegalArgumentException("Topic not found: " + metadata.getTopicName());
        }

        TopicEntity entity = existingEntity.get();
        entity.updateFromMetadata(metadata);

        topicRepository.save(entity);

        log.debug("Successfully updated metadata for topic: {}", metadata.getTopicName());

        // Push updated metadata to storage nodes
        try {
            metadataPushService.pushTopicMetadata(metadata);
            log.info("Successfully pushed topic metadata update for {}", metadata.getTopicName());
        } catch (Exception e) {
            log.error("Failed to push topic metadata update for {}: {}", metadata.getTopicName(), e.getMessage());
            // Don't fail the update if push fails
        }
    }

    @Override
    public void pushAllMetadataToService(String serviceUrl) {
        log.info("Pushing all metadata to service: {}", serviceUrl);

        // Only active controller can push metadata
        if (!raftController.isControllerLeader()) {
            throw new IllegalStateException("Only active controller can push metadata. Current leader: " +
                    raftController.getControllerLeaderId());
        }

        // Get all topics from database
        List<TopicEntity> allTopics = topicRepository.findAll();

        // Push each topic metadata to the requesting service
        for (TopicEntity topicEntity : allTopics) {
            TopicMetadata metadata = topicEntity.toMetadata();
            try {
                // Push to metadata service endpoint instead of storage service
                String metadataEndpoint = serviceUrl + "/api/v1/metadata/receive-metadata";
                restTemplate.postForObject(metadataEndpoint, createMetadataUpdateRequest(metadata), MetadataUpdateResponse.class);
                log.debug("Successfully pushed topic {} metadata to metadata service {}", metadata.getTopicName(), serviceUrl);
            } catch (Exception e) {
                log.error("Error pushing topic {} metadata to metadata service {}: {}", metadata.getTopicName(), serviceUrl, e.getMessage());
            }
        }

        // Push broker/cluster metadata
        try {
            String metadataEndpoint = serviceUrl + "/api/v1/metadata/receive-metadata";
            restTemplate.postForObject(metadataEndpoint, createClusterMetadataUpdateRequest(), MetadataUpdateResponse.class);
            log.debug("Successfully pushed cluster metadata to metadata service {}", serviceUrl);
        } catch (Exception e) {
            log.error("Error pushing cluster metadata to metadata service {}: {}", serviceUrl, e.getMessage());
        }

        log.info("Completed pushing all metadata to service: {}", serviceUrl);
    }

    @Override
    public void receiveMetadataFromController(com.distributedmq.common.dto.MetadataUpdateRequest metadataUpdate) {
        log.info("Receiving metadata from active controller");

        // Only non-active controllers should receive metadata
        if (raftController.isControllerLeader()) {
            log.warn("Active controller received metadata push - this should not happen");
            return;
        }

        // Process topic metadata
        if (metadataUpdate.getPartitions() != null) {
            for (com.distributedmq.common.dto.MetadataUpdateRequest.PartitionMetadata partition : metadataUpdate.getPartitions()) {
                // Group partitions by topic
                String topicName = partition.getTopic();
                TopicMetadata existingMetadata = metadataCache.get(topicName);

                if (existingMetadata == null) {
                    // Create new topic metadata
                    existingMetadata = TopicMetadata.builder()
                            .topicName(topicName)
                            .partitionCount(1) // Will be updated as more partitions are received
                            .replicationFactor(1) // Placeholder
                            .createdAt(System.currentTimeMillis())
                            .build();
                }

                // Add/update partition
                // TODO: Properly reconstruct TopicMetadata from partition data
                // For now, just mark that we have data for this topic
                metadataCache.put(topicName, existingMetadata);
            }
        }

        hasSyncedData = true;
        log.info("Successfully received and cached metadata from active controller");
    }

    @Override
    public HeartbeatResponse sendHeartbeat() {
        log.debug("Sending heartbeat to controller");

        try {
            // Get controller URL (placeholder - should use service discovery)
            String controllerUrl = getControllerUrl(raftController.getControllerLeaderId());

            HeartbeatRequest heartbeat = HeartbeatRequest.builder()
                    .serviceId(getCurrentServiceId()) // TODO: Get actual service ID
                    .lastMetadataUpdateTimestamp(getLastMetadataUpdateTimestamp())
                    .heartbeatTimestamp(System.currentTimeMillis())
                    .build();

            String heartbeatEndpoint = controllerUrl + "/api/v1/metadata/heartbeat";
            HeartbeatResponse response = restTemplate.postForObject(heartbeatEndpoint, heartbeat, HeartbeatResponse.class);

            log.debug("Heartbeat sent successfully. In sync: {}", response != null ? response.isInSync() : false);
            return response;

        } catch (RestClientException e) {
            log.warn("Failed to send heartbeat to controller: {}", e.getMessage());
            return HeartbeatResponse.builder()
                    .success(false)
                    .inSync(false)
                    .errorMessage("Failed to send heartbeat: " + e.getMessage())
                    .responseTimestamp(System.currentTimeMillis())
                    .build();
        }
    }

    @Override
    public Long getLastMetadataUpdateTimestamp() {
        // For non-active controllers, return the timestamp when we last received data
        // For active controller, return current time (always up to date)
        if (raftController.isControllerLeader()) {
            return System.currentTimeMillis();
        } else {
            // TODO: Track actual last update timestamp when receiving metadata
            // For now, return a timestamp based on when we synced
            return hasSyncedData ? System.currentTimeMillis() - 60000 : 0L; // 1 minute ago if synced, 0 if not
        }
    }

    @Override
    public MetadataUpdateResponse processStorageUpdate(MetadataUpdateRequest storageUpdate) {
        log.info("Processing metadata update from storage service");

        try {
            // Update local metadata information
            updateLocalMetadataFromStorage(storageUpdate);

            // If this is not the active controller, notify the controller
            if (!raftController.isControllerLeader()) {
                notifyControllerAboutStorageUpdate(storageUpdate);
            } else {
                // This is the controller - update controller metadata and propagate to other services
                updateControllerMetadataAndPropagate(storageUpdate);
            }

            // Push the update to the paired storage service
            pushUpdateToPairedStorageService(storageUpdate);

            return MetadataUpdateResponse.builder()
                    .success(true)
                    .processedTimestamp(System.currentTimeMillis())
                    .build();

        } catch (Exception e) {
            log.error("Error processing storage update: {}", e.getMessage());
            return MetadataUpdateResponse.builder()
                    .success(false)
                    .errorCode(MetadataUpdateResponse.ErrorCode.PROCESSING_ERROR)
                    .errorMessage("Failed to process storage update: " + e.getMessage())
                    .processedTimestamp(System.currentTimeMillis())
                    .build();
        }
    }

    /**
     * Create MetadataUpdateRequest from TopicMetadata
     */
    private com.distributedmq.common.dto.MetadataUpdateRequest createMetadataUpdateRequest(TopicMetadata topicMetadata) {
        // Convert partitions
        List<com.distributedmq.common.dto.MetadataUpdateRequest.PartitionMetadata> partitionMetadatas =
                topicMetadata.getPartitions().stream()
                        .map(this::convertPartitionMetadata)
                        .collect(Collectors.toList());

        // Get all active brokers for broker info
        List<com.distributedmq.common.model.BrokerNode> activeBrokers = controllerService.getActiveBrokers();
        List<com.distributedmq.common.dto.MetadataUpdateRequest.BrokerInfo> brokerInfos = activeBrokers.stream()
                .map(this::convertBrokerNodeToBrokerInfo)
                .collect(Collectors.toList());

        return com.distributedmq.common.dto.MetadataUpdateRequest.builder()
                .brokers(brokerInfos)
                .partitions(partitionMetadatas)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Create cluster metadata update request (brokers only)
     */
    private com.distributedmq.common.dto.MetadataUpdateRequest createClusterMetadataUpdateRequest() {
        List<com.distributedmq.common.model.BrokerNode> activeBrokers = controllerService.getActiveBrokers();
        List<com.distributedmq.common.dto.MetadataUpdateRequest.BrokerInfo> brokerInfos = activeBrokers.stream()
                .map(this::convertBrokerNodeToBrokerInfo)
                .collect(Collectors.toList());

        return com.distributedmq.common.dto.MetadataUpdateRequest.builder()
                .brokers(brokerInfos)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Convert PartitionMetadata to MetadataUpdateRequest.PartitionMetadata
     */
    private com.distributedmq.common.dto.MetadataUpdateRequest.PartitionMetadata convertPartitionMetadata(
            com.distributedmq.common.model.PartitionMetadata partitionMetadata) {

        return com.distributedmq.common.dto.MetadataUpdateRequest.PartitionMetadata.builder()
                .topic(partitionMetadata.getTopicName())
                .partition(partitionMetadata.getPartitionId())
                .leaderId(partitionMetadata.getLeader().getBrokerId())
                .followerIds(partitionMetadata.getReplicas().stream()
                        .map(broker -> broker.getBrokerId())
                        .filter(id -> !id.equals(partitionMetadata.getLeader().getBrokerId()))
                        .collect(Collectors.toList()))
                .isrIds(partitionMetadata.getIsr().stream()
                        .map(broker -> broker.getBrokerId())
                        .collect(Collectors.toList()))
                .leaderEpoch(System.currentTimeMillis())
                .build();
    }

    /**
     * Convert BrokerNode to MetadataUpdateRequest.BrokerInfo
     */
    private com.distributedmq.common.dto.MetadataUpdateRequest.BrokerInfo convertBrokerNodeToBrokerInfo(
            com.distributedmq.common.model.BrokerNode brokerNode) {
        return com.distributedmq.common.dto.MetadataUpdateRequest.BrokerInfo.builder()
                .id(brokerNode.getBrokerId())
                .host(brokerNode.getHost())
                .port(brokerNode.getPort())
                .isAlive(brokerNode.getStatus() == com.distributedmq.common.model.BrokerStatus.ONLINE)
                .lastHeartbeat(System.currentTimeMillis())
                .build();
    }

    /**
     * Get controller info from a peer metadata service
     * Used when this node doesn't know who the active controller is
     */
    private Integer getControllerIdFromPeer(String peerUrl) {
        try {
            String url = peerUrl + "/api/v1/metadata/controller";
            // TODO: Create proper response DTO
            // For now, assume we can get controller info
            log.debug("Getting controller info from peer: {}", peerUrl);
            // Placeholder - need to implement proper deserialization
            return null;
        } catch (RestClientException e) {
            log.warn("Failed to get controller info from peer {}: {}", peerUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Request the active controller to push metadata to this service
     */
    private void requestMetadataSyncFromController(String controllerUrl) {
        try {
            String url = controllerUrl + "/api/v1/metadata/sync";
            // Include this service's URL as a parameter so controller knows where to push
            String thisServiceUrl = "http://localhost:8080"; // TODO: Get actual service URL
            url += "?requestingServiceUrl=" + thisServiceUrl;

            log.info("Requesting metadata sync from controller: {}", url);

            // Make POST request to trigger sync
            restTemplate.postForObject(url, null, Void.class);

        } catch (RestClientException e) {
            log.error("Failed to request metadata sync from controller {}: {}", controllerUrl, e.getMessage());
            throw new RuntimeException("Failed to sync metadata from controller", e);
        }
    }

    /**
     * Synchronize metadata from active controller when data is missing
     */
    private void synchronizeMetadataIfNeeded() {
        if (raftController.isControllerLeader()) {
            return; // Active controller doesn't need to sync
        }

        if (hasSyncedData) {
            return; // Already have synced data
        }

        log.info("Metadata missing, triggering synchronization from active controller");

        // Step 1: Get controller ID from a peer metadata service
        Integer controllerId = getControllerIdFromPeer(getPeerMetadataServiceUrl());
        if (controllerId == null) {
            throw new IllegalStateException("Cannot determine active controller ID from peers");
        }

        // Step 2: Request controller to push metadata to this service
        String controllerUrl = getControllerUrl(controllerId);
        requestMetadataSyncFromController(controllerUrl);

        // Note: hasSyncedData will be set to true when data is received via receiveMetadataFromController
        // For now, we'll assume the sync was successful and data will arrive soon
        log.info("Metadata synchronization request sent to controller {}", controllerId);
    }

    /**
     * Get URL of a peer metadata service (placeholder implementation)
     * TODO: Implement proper service discovery
     */
    private String getPeerMetadataServiceUrl() {
        // Placeholder: try a few known peers
        // In real implementation, this would use service discovery
        String[] peerUrls = {"http://localhost:8081", "http://localhost:8082", "http://localhost:8083"};

        for (String url : peerUrls) {
            try {
                // Quick check if service is available
                restTemplate.getForObject(url + "/api/v1/metadata/controller", String.class);
                return url;
            } catch (Exception e) {
                log.debug("Peer {} not available: {}", url, e.getMessage());
            }
        }

        throw new IllegalStateException("No peer metadata services available for controller discovery");
    }

    /**
     * Information about a registered metadata service
     */
    private static class MetadataServiceInfo {
        private final Integer serviceId;
        private volatile Long lastHeartbeatTimestamp;
        private volatile Long lastMetadataUpdateTimestamp;

        public MetadataServiceInfo(Integer serviceId, Long lastHeartbeatTimestamp) {
            this.serviceId = serviceId;
            this.lastHeartbeatTimestamp = lastHeartbeatTimestamp;
            this.lastMetadataUpdateTimestamp = 0L;
        }

        public Integer getServiceId() { return serviceId; }
        public Long getLastHeartbeatTimestamp() { return lastHeartbeatTimestamp; }
        public void setLastHeartbeatTimestamp(Long timestamp) { this.lastHeartbeatTimestamp = timestamp; }
        public Long getLastMetadataUpdateTimestamp() { return lastMetadataUpdateTimestamp; }
        public void setLastMetadataUpdateTimestamp(Long timestamp) { this.lastMetadataUpdateTimestamp = timestamp; }
    }

    /**
     * Register or update a metadata service in the registry
     */
    private void registerMetadataService(Integer serviceId, Long heartbeatTimestamp) {
        MetadataServiceInfo info = metadataServiceRegistry.computeIfAbsent(serviceId,
                id -> new MetadataServiceInfo(id, heartbeatTimestamp));
        info.setLastHeartbeatTimestamp(heartbeatTimestamp);
        log.debug("Registered/updated metadata service: {}", serviceId);
    }

    /**
     * Get the controller's current metadata timestamp (truth value)
     */
    private Long getControllerMetadataTimestamp() {
        // The controller's timestamp is the current time, as it's always up to date
        // In a real implementation, this could be the timestamp of the last committed metadata change
        return System.currentTimeMillis();
    }

    /**
     * Trigger metadata sync for a lagging service
     */
    private void triggerMetadataSyncForService(Integer serviceId) {
        try {
            // Get the service URL (placeholder - should use service discovery)
            String serviceUrl = getMetadataServiceUrl(serviceId);

            // Push all current metadata to the lagging service
            pushAllMetadataToService(serviceUrl);

            // Update the service's last metadata update timestamp
            MetadataServiceInfo info = metadataServiceRegistry.get(serviceId);
            if (info != null) {
                info.setLastMetadataUpdateTimestamp(System.currentTimeMillis());
            }

            log.info("Successfully triggered metadata sync for lagging service: {}", serviceId);

        } catch (Exception e) {
            log.error("Failed to trigger metadata sync for service {}: {}", serviceId, e.getMessage());
        }
    }

    /**
     * Get URL for a metadata service (placeholder implementation)
     */
    private String getMetadataServiceUrl(Integer serviceId) {
        // Placeholder: assume services run on localhost with ports 8080, 8081, 8082
        int basePort = 8080;
        return "http://localhost:" + (basePort + serviceId);
    }

    /**
     * Get controller URL from controller ID
     */
    private String getControllerUrl(Integer controllerId) {
        // Placeholder: assume controllers run on localhost with ports 8080, 8081, 8082
        int basePort = 8080;
        return "http://localhost:" + (basePort + controllerId);
    }

    /**
     * Get the current service ID (placeholder implementation)
     * TODO: Implement proper service ID discovery
     */
    private Integer getCurrentServiceId() {
        // Placeholder: assume service ID based on port or configuration
        // In real implementation, this would be injected from configuration
        return 0; // Default service ID
    }

    /**
     * Update local metadata from storage service update
     */
    private void updateLocalMetadataFromStorage(MetadataUpdateRequest storageUpdate) {
        log.debug("Updating local metadata from storage update");

        // For non-controller metadata services, update the in-memory cache
        if (!raftController.isControllerLeader()) {
            // Process partition updates
            if (storageUpdate.getPartitions() != null) {
                for (MetadataUpdateRequest.PartitionMetadata partition : storageUpdate.getPartitions()) {
                    String topicName = partition.getTopic();
                    TopicMetadata existingMetadata = metadataCache.get(topicName);

                    if (existingMetadata == null) {
                        // Create basic topic metadata if it doesn't exist
                        existingMetadata = TopicMetadata.builder()
                                .topicName(topicName)
                                .partitionCount(1)
                                .replicationFactor(1)
                                .createdAt(System.currentTimeMillis())
                                .build();
                    }

                    // Update cache with new information
                    metadataCache.put(topicName, existingMetadata);
                }
            }
        }

        // Update last metadata timestamp
        // This will be used for heartbeat synchronization
        log.debug("Local metadata updated from storage service");
    }

    /**
     * Notify the active controller about storage update
     */
    private void notifyControllerAboutStorageUpdate(MetadataUpdateRequest storageUpdate) {
        try {
            // Get controller URL
            String controllerUrl = getControllerUrl(raftController.getControllerLeaderId());
            String endpoint = controllerUrl + "/api/v1/metadata/storage-updates";

            log.info("Notifying controller about storage update: {}", controllerUrl);

            // Send the update to controller
            MetadataUpdateResponse response = restTemplate.postForObject(endpoint, storageUpdate, MetadataUpdateResponse.class);

            if (response != null && response.isSuccess()) {
                log.info("Successfully notified controller about storage update");
            } else {
                log.warn("Controller notification failed: {}", response != null ? response.getErrorMessage() : "null response");
            }

        } catch (Exception e) {
            log.error("Failed to notify controller about storage update: {}", e.getMessage());
        }
    }

    /**
     * Update controller metadata and propagate to other services
     */
    private void updateControllerMetadataAndPropagate(MetadataUpdateRequest storageUpdate) {
        log.info("Controller updating metadata and propagating to other services");

        // Update controller's database with the new information
        updateControllerDatabaseFromStorage(storageUpdate);

        // Propagate the update to all other metadata services
        propagateUpdateToAllMetadataServices(storageUpdate);

        log.info("Controller metadata update and propagation completed");
    }

    /**
     * Update controller's database from storage update
     */
    private void updateControllerDatabaseFromStorage(MetadataUpdateRequest storageUpdate) {
        // Process partition leadership changes
        if (storageUpdate.getPartitions() != null) {
            for (MetadataUpdateRequest.PartitionMetadata partition : storageUpdate.getPartitions()) {
                // Update partition leadership in controller's view
                controllerService.updatePartitionLeadership(
                        partition.getTopic(),
                        partition.getPartition(),
                        partition.getLeaderId(),
                        partition.getFollowerIds(),
                        partition.getIsrIds()
                );
            }
        }

        // Process broker status changes
        if (storageUpdate.getBrokers() != null) {
            for (MetadataUpdateRequest.BrokerInfo brokerInfo : storageUpdate.getBrokers()) {
                if (!brokerInfo.isAlive()) {
                    // Handle broker failure
                    controllerService.handleBrokerFailure(brokerInfo.getId());
                } else {
                    // Update broker information
                    controllerService.registerBroker(BrokerNode.builder()
                            .brokerId(brokerInfo.getId())
                            .host(brokerInfo.getHost())
                            .port(brokerInfo.getPort())
                            .status(BrokerStatus.ONLINE)
                            .build());
                }
            }
        }

        log.debug("Controller database updated from storage update");
    }

    /**
     * Propagate update to all registered metadata services
     */
    private void propagateUpdateToAllMetadataServices(MetadataUpdateRequest storageUpdate) {
        // Get all registered metadata services (from heartbeat registry)
        for (Integer serviceId : metadataServiceRegistry.keySet()) {
            try {
                // Don't send back to the service that sent the original update
                if (serviceId.equals(getCurrentServiceId())) {
                    continue;
                }

                String serviceUrl = getMetadataServiceUrl(serviceId);
                String endpoint = serviceUrl + "/api/v1/metadata/receive-metadata";

                log.debug("Propagating storage update to metadata service {}: {}", serviceId, serviceUrl);

                // Send the update to other metadata services
                restTemplate.postForObject(endpoint, storageUpdate, MetadataUpdateResponse.class);

                // Update the service's last metadata timestamp
                MetadataServiceInfo info = metadataServiceRegistry.get(serviceId);
                if (info != null) {
                    info.setLastMetadataUpdateTimestamp(System.currentTimeMillis());
                }

            } catch (Exception e) {
                log.error("Failed to propagate update to metadata service {}: {}", serviceId, e.getMessage());
            }
        }

        log.info("Propagated storage update to all metadata services");
    }

    /**
     * Push metadata update to the paired storage service
     */
    private void pushUpdateToPairedStorageService(MetadataUpdateRequest update) {
        try {
            // Get this metadata service's ID (placeholder - should be injected)
            Integer thisServiceId = getCurrentServiceId();
            if (thisServiceId == null) {
                log.warn("Cannot determine this service's ID, skipping storage push");
                return;
            }

            // Find the paired storage service
            Integer pairedStorageServiceId = findPairedStorageServiceId(thisServiceId);
            if (pairedStorageServiceId == null) {
                log.warn("No paired storage service found for metadata service {}", thisServiceId);
                return;
            }

            // Get storage service URL
            String storageServiceUrl = com.distributedmq.common.config.ServiceDiscovery.getStorageServiceUrl(pairedStorageServiceId);
            if (storageServiceUrl == null) {
                log.warn("No URL found for storage service {}", pairedStorageServiceId);
                return;
            }

            // Create the update request with version
            MetadataUpdateRequest versionedUpdate = MetadataUpdateRequest.builder()
                    .version(System.currentTimeMillis()) // Use timestamp as version for now
                    .brokers(update.getBrokers())
                    .partitions(update.getPartitions())
                    .timestamp(update.getTimestamp())
                    .build();

            // Push to storage service
            String endpoint = storageServiceUrl + "/api/v1/storage/metadata";
            restTemplate.postForObject(endpoint, versionedUpdate, MetadataUpdateResponse.class);

            log.info("Pushed metadata update to paired storage service {}: {}", pairedStorageServiceId, storageServiceUrl);

        } catch (Exception e) {
            log.error("Failed to push update to paired storage service: {}", e.getMessage());
        }
    }

    /**
     * Find the paired storage service ID for a metadata service
     */
    private Integer findPairedStorageServiceId(Integer metadataServiceId) {
        // Look through all storage services to find the one paired with this metadata service
        List<com.distributedmq.common.config.ServiceDiscovery.StorageServiceInfo> storageServices =
            com.distributedmq.common.config.ServiceDiscovery.getAllStorageServices();

        for (com.distributedmq.common.config.ServiceDiscovery.StorageServiceInfo storageService : storageServices) {
            if (metadataServiceId.equals(storageService.getPairedMetadataServiceId())) {
                return storageService.getId();
            }
        }

        return null;
    }
}
