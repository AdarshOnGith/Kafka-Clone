package com.distributedmq.metadata.controller;

import com.distributedmq.common.dto.HeartbeatRequest;
import com.distributedmq.common.dto.HeartbeatResponse;
import com.distributedmq.common.dto.StorageHeartbeatRequest;
import com.distributedmq.common.dto.StorageHeartbeatResponse;
import com.distributedmq.common.dto.MetadataUpdateRequest;
import com.distributedmq.common.dto.MetadataUpdateResponse;
import com.distributedmq.common.model.TopicMetadata;
import com.distributedmq.metadata.coordination.RaftController;
import com.distributedmq.metadata.dto.CreateTopicRequest;
import com.distributedmq.metadata.dto.TopicMetadataResponse;
import com.distributedmq.metadata.service.MetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST Controller for Metadata operations
 * Entry point with request validation
 *
 * When metadata is pushed to storage nodes then storage node should be consistent with 
 * existing operations ( handle it properly different possibilities like, stop all operations update metadata and then start operations again with new data, or let current operations finish and then update metadata for new operations )
 * 
 * Routes requests to controller leader in KRaft mode
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataService metadataService;
    private final RaftController raftController;

    // Registry of metadata services for heartbeat tracking
    private final Map<Integer, MetadataServiceInfo> metadataServiceRegistry = new ConcurrentHashMap<>();

    // Registry of storage services for heartbeat tracking
    private final Map<Integer, StorageServiceInfo> storageServiceRegistry = new ConcurrentHashMap<>();

    /**
     * Create a new topic
     * Only controller leader can process this
     */
    @PostMapping("/topics")
    public ResponseEntity<TopicMetadataResponse> createTopic(
            @Validated @RequestBody CreateTopicRequest request) {
        
        log.info("Received request to create topic: {}", request.getTopicName());
        
        // Check if this node is the controller leader
        if (!raftController.isControllerLeader()) {
            log.warn("This node is not the controller leader. Current leader: {}", 
                    raftController.getControllerLeaderId());
            // TODO: Return redirect to leader or forward request
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Controller-Leader", raftController.getControllerLeaderId().toString())
                    .build();
        }
        
        try {
            TopicMetadata metadata = metadataService.createTopic(request);
            
            TopicMetadataResponse response = TopicMetadataResponse.builder()
                    .topicName(metadata.getTopicName())
                    .partitionCount(metadata.getPartitionCount())
                    .replicationFactor(metadata.getReplicationFactor())
                    .partitions(metadata.getPartitions())
                    .createdAt(metadata.getCreatedAt())
                    .config(metadata.getConfig())
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Topic creation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating topic", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get topic metadata
     * Can be served by any node (read operation)
     */
    @GetMapping("/topics/{topicName}")
    public ResponseEntity<TopicMetadataResponse> getTopicMetadata(
            @PathVariable String topicName) {
        
        log.debug("Fetching metadata for topic: {}", topicName);
        
        try {
            TopicMetadata metadata = metadataService.getTopicMetadata(topicName);
            
            TopicMetadataResponse response = TopicMetadataResponse.builder()
                    .topicName(metadata.getTopicName())
                    .partitionCount(metadata.getPartitionCount())
                    .replicationFactor(metadata.getReplicationFactor())
                    .partitions(metadata.getPartitions())
                    .createdAt(metadata.getCreatedAt())
                    .config(metadata.getConfig())
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Topic not found: {}", topicName);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching topic metadata", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * List all topics
     * Can be served by any node (read operation)
     */
    @GetMapping("/topics")
    public ResponseEntity<List<String>> listTopics() {
        log.debug("Listing all topics");
        
        try {
            List<String> topics = metadataService.listTopics();
            return ResponseEntity.ok(topics);
            
        } catch (Exception e) {
            log.error("Error listing topics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a topic
     * Only controller leader can process this
     */
    @DeleteMapping("/topics/{topicName}")
    public ResponseEntity<Void> deleteTopic(@PathVariable String topicName) {
        log.info("Received request to delete topic: {}", topicName);
        
        // Check if this node is the controller leader
        if (!raftController.isControllerLeader()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Controller-Leader", raftController.getControllerLeaderId().toString())
                    .build();
        }
        
        try {
            metadataService.deleteTopic(topicName);
            return ResponseEntity.noContent().build();
            
        } catch (IllegalArgumentException e) {
            log.warn("Topic deletion failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting topic", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get leader for a partition
     */
    @GetMapping("/topics/{topicName}/partitions/{partition}/leader")
    public ResponseEntity<?> getPartitionLeader(
            @PathVariable String topicName,
            @PathVariable Integer partition) {
        
        log.debug("Fetching leader for topic: {}, partition: {}", topicName, partition);
        
        // TODO: Implement partition leader lookup
        
        return ResponseEntity.ok().build();
    }

    /**
     * Get controller leader info
     */
    @GetMapping("/controller")
    public ResponseEntity<ControllerInfo> getControllerInfo() {
        return ResponseEntity.ok(new ControllerInfo(
                raftController.isControllerLeader(),
                raftController.getControllerLeaderId(),
                raftController.getCurrentTerm()
        ));
    }

    /**
     * Request metadata synchronization (called by other metadata services)
     * Only the active controller can handle sync requests
     */
    @PostMapping("/sync")
    public ResponseEntity<Void> requestMetadataSync(@RequestParam String requestingServiceUrl) {
        log.info("Received metadata sync request from: {}", requestingServiceUrl);

        // Only active controller can handle sync requests
        if (!raftController.isControllerLeader()) {
            log.warn("Non-active controller received sync request. Current leader: {}",
                    raftController.getControllerLeaderId());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Controller-Leader", raftController.getControllerLeaderId().toString())
                    .build();
        }

        try {
            // Push all current metadata to the requesting service
            metadataService.pushAllMetadataToService(requestingServiceUrl);
            log.info("Successfully pushed all metadata to requesting service: {}", requestingServiceUrl);

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error processing metadata sync request from {}: {}", requestingServiceUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Receive metadata updates from storage services
     * Storage services notify metadata services about local changes (leadership, failures, etc.)
     */
    @PostMapping("/storage-updates")
    public ResponseEntity<MetadataUpdateResponse> receiveStorageUpdate(@RequestBody MetadataUpdateRequest storageUpdate) {
        log.info("Received metadata update from storage service with {} brokers and {} partitions",
                storageUpdate.getBrokers() != null ? storageUpdate.getBrokers().size() : 0,
                storageUpdate.getPartitions() != null ? storageUpdate.getPartitions().size() : 0);

        try {
            MetadataUpdateResponse response = metadataService.processStorageUpdate(storageUpdate);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing storage update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MetadataUpdateResponse.builder()
                            .success(false)
                            .errorCode(MetadataUpdateResponse.ErrorCode.PROCESSING_ERROR)
                            .errorMessage("Failed to process storage update: " + e.getMessage())
                            .processedTimestamp(System.currentTimeMillis())
                            .build());
        }
    }

    /**
     * Receive heartbeat from metadata services
     * Controller uses this to track sync status and trigger updates for lagging services
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<HeartbeatResponse> receiveHeartbeat(@RequestBody HeartbeatRequest heartbeat) {
        log.debug("Received heartbeat from metadata service: {}", heartbeat.getServiceId());

        try {
            // Process heartbeat directly in controller
            HeartbeatResponse response = processHeartbeat(heartbeat);

            if (!response.isInSync()) {
                log.info("Metadata service {} is out of sync. Triggering metadata push...", heartbeat.getServiceId());
                // The processHeartbeat method should have already triggered the sync
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing heartbeat from service {}: {}", heartbeat.getServiceId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(HeartbeatResponse.builder()
                            .success(false)
                            .inSync(false)
                            .errorMessage("Failed to process heartbeat: " + e.getMessage())
                            .responseTimestamp(System.currentTimeMillis())
                            .build());
        }
    }

    /**
     * Process heartbeat from metadata service
     */
    private HeartbeatResponse processHeartbeat(HeartbeatRequest heartbeat) {
        log.debug("Processing heartbeat from metadata service: {}", heartbeat.getServiceId());

        // Only active controller should process heartbeats
        if (!raftController.isControllerLeader()) {
            log.warn("Non-active controller received heartbeat - this should not happen");
            return HeartbeatResponse.builder()
                    .success(false)
                    .inSync(false)
                    .errorMessage("Not the active controller")
                    .responseTimestamp(System.currentTimeMillis())
                    .build();
        }

        try {
            // Register/update the metadata service
            registerMetadataService(heartbeat.getServiceId(), heartbeat.getHeartbeatTimestamp());

            // Get controller's truth timestamp
            Long controllerTimestamp = getControllerMetadataTimestamp();

            // Check if service is in sync
            boolean inSync = heartbeat.getLastMetadataUpdateTimestamp() >= (controllerTimestamp - 5000); // 5 second tolerance

            HeartbeatResponse response = HeartbeatResponse.builder()
                    .success(true)
                    .inSync(inSync)
                    .controllerMetadataTimestamp(controllerTimestamp)
                    .responseTimestamp(System.currentTimeMillis())
                    .build();

            // If service is lagging, trigger metadata sync
            if (!inSync) {
                log.info("Metadata service {} is lagging (service: {}, controller: {}). Triggering sync...",
                        heartbeat.getServiceId(), heartbeat.getLastMetadataUpdateTimestamp(), controllerTimestamp);

                triggerMetadataSyncForService(heartbeat.getServiceId());
            }

            return response;

        } catch (Exception e) {
            log.error("Error processing heartbeat from service {}: {}", heartbeat.getServiceId(), e.getMessage());
            return HeartbeatResponse.builder()
                    .success(false)
                    .inSync(false)
                    .errorMessage("Failed to process heartbeat: " + e.getMessage())
                    .responseTimestamp(System.currentTimeMillis())
                    .build();
        }
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
            metadataService.pushAllMetadataToService(serviceUrl);

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
     * Receive heartbeat from storage services
     * Controller uses this to track storage service sync status and detect failures
     */
    @PostMapping("/storage-heartbeat")
    public ResponseEntity<StorageHeartbeatResponse> receiveStorageHeartbeat(@RequestBody StorageHeartbeatRequest heartbeat) {
        log.debug("Received storage heartbeat from service: {}", heartbeat.getStorageServiceId());

        try {
            // Process storage heartbeat directly in controller
            StorageHeartbeatResponse response = processStorageHeartbeat(heartbeat);

            if (!response.isInSync()) {
                log.info("Storage service {} is out of sync. Triggering sync...", heartbeat.getStorageServiceId());
                // The processStorageHeartbeat method should have already triggered the sync
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing storage heartbeat from service {}: {}", heartbeat.getStorageServiceId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StorageHeartbeatResponse.builder()
                            .success(false)
                            .inSync(false)
                            .errorMessage("Failed to process heartbeat: " + e.getMessage())
                            .responseTimestamp(System.currentTimeMillis())
                            .build());
        }
    }

    /**
     * Process heartbeat from storage service
     */
    private StorageHeartbeatResponse processStorageHeartbeat(StorageHeartbeatRequest heartbeat) {
        log.debug("Processing storage heartbeat from service: {}", heartbeat.getStorageServiceId());

        // Only active controller should process storage heartbeats
        if (!raftController.isControllerLeader()) {
            log.warn("Non-active controller received storage heartbeat - this should not happen");
            return StorageHeartbeatResponse.builder()
                    .success(false)
                    .inSync(false)
                    .errorMessage("Not the active controller")
                    .responseTimestamp(System.currentTimeMillis())
                    .build();
        }

        try {
            // Register/update the storage service
            registerStorageService(heartbeat);

            // Get controller's truth metadata version
            Long controllerVersion = getControllerMetadataVersion();

            // Check if storage service is in sync
            boolean inSync = heartbeat.getCurrentMetadataVersion() != null &&
                           heartbeat.getCurrentMetadataVersion() >= controllerVersion;

            StorageHeartbeatResponse response = StorageHeartbeatResponse.builder()
                    .success(true)
                    .controllerMetadataVersion(controllerVersion)
                    .inSync(inSync)
                    .responseTimestamp(System.currentTimeMillis())
                    .build();

            // If service is lagging, trigger metadata sync
            if (!inSync) {
                log.info("Storage service {} is lagging (service: {}, controller: {}). Triggering sync...",
                        heartbeat.getStorageServiceId(), heartbeat.getCurrentMetadataVersion(), controllerVersion);

                triggerMetadataSyncForStorageService(heartbeat.getStorageServiceId());
            }

            // Check if this is a failure detection case (service was down and came back)
            StorageServiceInfo existingInfo = storageServiceRegistry.get(heartbeat.getStorageServiceId());
            if (existingInfo != null && !existingInfo.isAlive() && heartbeat.isAlive()) {
                log.info("Storage service {} came back online, triggering leader election and ISR updates",
                        heartbeat.getStorageServiceId());
                handleStorageServiceRecovery(heartbeat.getStorageServiceId());
            }

            return response;

        } catch (Exception e) {
            log.error("Error processing storage heartbeat from service {}: {}", heartbeat.getStorageServiceId(), e.getMessage());
            return StorageHeartbeatResponse.builder()
                    .success(false)
                    .inSync(false)
                    .errorMessage("Failed to process heartbeat: " + e.getMessage())
                    .responseTimestamp(System.currentTimeMillis())
                    .build();
        }
    }

    // TODO: Add endpoints for consumer group management
    // TODO: Add endpoints for offset management
    // TODO: Add cluster health endpoints

    /**
     * Register or update a storage service in the registry
     */
    private void registerStorageService(StorageHeartbeatRequest heartbeat) {
        StorageServiceInfo info = storageServiceRegistry.computeIfAbsent(heartbeat.getStorageServiceId(),
                id -> new StorageServiceInfo(id, heartbeat.getHeartbeatTimestamp()));
        info.setLastHeartbeatTimestamp(heartbeat.getHeartbeatTimestamp());
        info.setCurrentMetadataVersion(heartbeat.getCurrentMetadataVersion());
        info.setLastMetadataUpdateTimestamp(heartbeat.getLastMetadataUpdateTimestamp());
        info.setAlive(heartbeat.isAlive());
        info.setPartitionsLeading(heartbeat.getPartitionsLeading());
        info.setPartitionsFollowing(heartbeat.getPartitionsFollowing());
        log.debug("Registered/updated storage service: {}", heartbeat.getStorageServiceId());
    }

    /**
     * Get the controller's current metadata version (truth value)
     */
    private Long getControllerMetadataVersion() {
        // The controller's version is the current time as version number
        // In a real implementation, this could be an incrementing version number
        return System.currentTimeMillis();
    }

    /**
     * Trigger metadata sync for a lagging storage service
     */
    private void triggerMetadataSyncForStorageService(Integer storageServiceId) {
        try {
            // Get the paired metadata service for this storage service
            Integer pairedMetadataServiceId = com.distributedmq.common.config.ServiceDiscovery.getPairedMetadataServiceId(storageServiceId);
            if (pairedMetadataServiceId == null) {
                log.warn("No paired metadata service found for storage service {}", storageServiceId);
                return;
            }

            // Get the metadata service URL
            String metadataServiceUrl = com.distributedmq.common.config.ServiceDiscovery.getMetadataServiceUrl(pairedMetadataServiceId);
            if (metadataServiceUrl == null) {
                log.warn("No URL found for metadata service {}", pairedMetadataServiceId);
                return;
            }

            // Push all current metadata to the metadata service, which will then push to storage
            metadataService.pushAllMetadataToService(metadataServiceUrl);

            // Update the storage service's last metadata timestamp
            StorageServiceInfo info = storageServiceRegistry.get(storageServiceId);
            if (info != null) {
                info.setLastMetadataUpdateTimestamp(System.currentTimeMillis());
                info.setCurrentMetadataVersion(getControllerMetadataVersion());
            }

            log.info("Successfully triggered metadata sync for storage service: {}", storageServiceId);

        } catch (Exception e) {
            log.error("Failed to trigger metadata sync for storage service {}: {}", storageServiceId, e.getMessage());
        }
    }

    /**
     * Handle storage service recovery (came back online)
     */
    private void handleStorageServiceRecovery(Integer storageServiceId) {
        try {
            log.info("Handling recovery of storage service {}", storageServiceId);

            // Perform partition leader election for partitions that were led by this broker
            // This would involve checking which partitions this broker was leading before failure
            // and electing new leaders if necessary

            // Update ISR lists to remove this broker if it was in ISR but not responsive

            // Trigger metadata updates to all services
            updateAllServicesWithRecoveryInfo(storageServiceId);

        } catch (Exception e) {
            log.error("Failed to handle storage service recovery for {}: {}", storageServiceId, e.getMessage());
        }
    }

    /**
     * Update all services with recovery information
     */
    private void updateAllServicesWithRecoveryInfo(Integer storageServiceId) {
        // This would trigger the bidirectional flow to update all metadata services
        // and their paired storage services about the recovery
        log.info("Updating all services about recovery of storage service {}", storageServiceId);
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
     * Information about a registered storage service
     */
    private static class StorageServiceInfo {
        private final Integer serviceId;
        private volatile Long lastHeartbeatTimestamp;
        private volatile Long currentMetadataVersion;
        private volatile Long lastMetadataUpdateTimestamp;
        private volatile boolean alive;
        private volatile Integer partitionsLeading;
        private volatile Integer partitionsFollowing;

        public StorageServiceInfo(Integer serviceId, Long lastHeartbeatTimestamp) {
            this.serviceId = serviceId;
            this.lastHeartbeatTimestamp = lastHeartbeatTimestamp;
            this.currentMetadataVersion = 0L;
            this.lastMetadataUpdateTimestamp = 0L;
            this.alive = true;
            this.partitionsLeading = 0;
            this.partitionsFollowing = 0;
        }

        public Integer getServiceId() { return serviceId; }
        public Long getLastHeartbeatTimestamp() { return lastHeartbeatTimestamp; }
        public void setLastHeartbeatTimestamp(Long timestamp) { this.lastHeartbeatTimestamp = timestamp; }
        public Long getCurrentMetadataVersion() { return currentMetadataVersion; }
        public void setCurrentMetadataVersion(Long version) { this.currentMetadataVersion = version; }
        public Long getLastMetadataUpdateTimestamp() { return lastMetadataUpdateTimestamp; }
        public void setLastMetadataUpdateTimestamp(Long timestamp) { this.lastMetadataUpdateTimestamp = timestamp; }
        public boolean isAlive() { return alive; }
        public void setAlive(boolean alive) { this.alive = alive; }
        public Integer getPartitionsLeading() { return partitionsLeading; }
        public void setPartitionsLeading(Integer partitionsLeading) { this.partitionsLeading = partitionsLeading; }
        public Integer getPartitionsFollowing() { return partitionsFollowing; }
        public void setPartitionsFollowing(Integer partitionsFollowing) { this.partitionsFollowing = partitionsFollowing; }
    }
    
    public static class ControllerInfo {
        private final boolean isLeader;
        private final Integer leaderId;
        private final long term;
        
        public ControllerInfo(boolean isLeader, Integer leaderId, long term) {
            this.isLeader = isLeader;
            this.leaderId = leaderId;
            this.term = term;
        }
        
        public boolean isLeader() { return isLeader; }
        public Integer getLeaderId() { return leaderId; }
        public long getTerm() { return term; }
    }
}
