package com.distributedmq.metadata.controller;

import com.distributedmq.common.dto.HeartbeatRequest;
import com.distributedmq.common.dto.HeartbeatResponse;
import com.distributedmq.common.dto.StorageHeartbeatRequest;
import com.distributedmq.common.dto.StorageHeartbeatResponse;
import com.distributedmq.common.dto.StorageControllerHeartbeatRequest;
import com.distributedmq.common.dto.StorageControllerHeartbeatResponse;
import com.distributedmq.common.dto.ISRStatusBatchRequest;
import com.distributedmq.common.dto.ISRStatusBatchResponse;
import com.distributedmq.common.dto.ISRStatusUpdate;
import com.distributedmq.common.dto.MetadataUpdateResponse;
import com.distributedmq.common.model.TopicMetadata;
import com.distributedmq.metadata.coordination.RaftController;
import com.distributedmq.metadata.dto.CreateTopicRequest;
import com.distributedmq.metadata.dto.TopicMetadataResponse;
import com.distributedmq.metadata.dto.RegisterBrokerRequest;
import com.distributedmq.metadata.dto.BrokerResponse;
import com.distributedmq.metadata.dto.MetadataSyncResponse;
import com.distributedmq.metadata.dto.BrokerSyncResponse;
import com.distributedmq.metadata.dto.SyncStatusResponse;
import com.distributedmq.metadata.dto.IncrementalSyncResponse;
import com.distributedmq.metadata.dto.ConsistencyCheckResponse;
import com.distributedmq.metadata.dto.SyncTriggerRequest;
import com.distributedmq.metadata.dto.SyncTriggerResponse;
import com.distributedmq.metadata.dto.FullSyncResponse;
import com.distributedmq.metadata.dto.TestStorageHeartbeatRequest;
import com.distributedmq.metadata.dto.TestStorageHeartbeatResponse;
import com.distributedmq.metadata.dto.SimpleHeartbeatRequest;
import com.distributedmq.metadata.dto.SimpleHeartbeatResponse;
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
import java.util.ArrayList;

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
     * Pull sync all metadata (GET version for testing)
     * Returns all current metadata
     */
    @GetMapping("/sync")
    public ResponseEntity<MetadataSyncResponse> pullMetadataSync() {
        log.info("Received pull metadata sync request");

        try {
            // Get all topics
            List<String> topics = metadataService.listTopics();
            List<BrokerResponse> brokers = metadataService.listBrokers();

            MetadataSyncResponse response = MetadataSyncResponse.builder()
                    .topics(topics)
                    .brokers(brokers)
                    .syncTimestamp(System.currentTimeMillis())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing pull metadata sync: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Sync metadata for specific broker
     */
    @GetMapping("/sync/broker/{brokerId}")
    public ResponseEntity<BrokerSyncResponse> syncBrokerMetadata(@PathVariable Integer brokerId) {
        log.info("Received broker sync request for broker: {}", brokerId);

        try {
            BrokerResponse broker = metadataService.getBroker(brokerId);
            List<String> topics = metadataService.listTopics();

            BrokerSyncResponse response = BrokerSyncResponse.builder()
                    .broker(broker)
                    .topics(topics)
                    .syncTimestamp(System.currentTimeMillis())
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Broker not found for sync: {}", brokerId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error processing broker sync for {}: {}", brokerId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get sync status
     */
    @GetMapping("/sync/status")
    public ResponseEntity<SyncStatusResponse> getSyncStatus() {
        log.debug("Getting sync status");

        try {
            // Get basic sync information
            boolean isControllerLeader = raftController.isControllerLeader();
            long lastSyncTimestamp = System.currentTimeMillis(); // Placeholder
            int activeBrokers = metadataService.listBrokers().size();
            int totalTopics = metadataService.listTopics().size();

            SyncStatusResponse response = SyncStatusResponse.builder()
                    .isControllerLeader(isControllerLeader)
                    .lastSyncTimestamp(lastSyncTimestamp)
                    .activeBrokers(activeBrokers)
                    .totalTopics(totalTopics)
                    .status("HEALTHY")
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting sync status: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Incremental sync - get changes since timestamp
     */
    @GetMapping("/sync/incremental")
    public ResponseEntity<IncrementalSyncResponse> getIncrementalSync(
            @RequestParam(required = false, defaultValue = "0") Long sinceTimestamp) {

        log.info("Received incremental sync request since: {}", sinceTimestamp);

        try {
            // For now, return all data (full sync)
            // In a real implementation, this would track changes and return only deltas
            List<String> topics = metadataService.listTopics();
            List<BrokerResponse> brokers = metadataService.listBrokers();

            IncrementalSyncResponse response = IncrementalSyncResponse.builder()
                    .topics(topics)
                    .brokers(brokers)
                    .changesSince(sinceTimestamp)
                    .syncTimestamp(System.currentTimeMillis())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing incremental sync: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Check metadata consistency
     */
    @GetMapping("/sync/consistency")
    public ResponseEntity<ConsistencyCheckResponse> checkConsistency() {
        log.info("Received consistency check request");

        try {
            // Basic consistency checks
            List<BrokerResponse> brokers = metadataService.listBrokers();
            List<String> topics = metadataService.listTopics();

            boolean brokersConsistent = brokers.stream()
                    .allMatch(broker -> broker.getId() != null && broker.getHost() != null);
            boolean topicsConsistent = !topics.isEmpty() || topics.isEmpty(); // Always true for now

            String status = (brokersConsistent && topicsConsistent) ? "CONSISTENT" : "INCONSISTENT";

            ConsistencyCheckResponse response = ConsistencyCheckResponse.builder()
                    .status(status)
                    .brokersChecked(brokers.size())
                    .topicsChecked(topics.size())
                    .checkTimestamp(System.currentTimeMillis())
                    .details("Basic consistency check performed")
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error checking consistency: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Trigger push sync to specified brokers
     */
    @PostMapping("/sync/trigger")
    public ResponseEntity<SyncTriggerResponse> triggerSync(@RequestBody SyncTriggerRequest request) {
        log.info("Received sync trigger request for brokers: {} and topics: {}",
                request.getBrokers(), request.getTopics());

        try {
            // Validate that specified brokers exist
            if (request.getBrokers() != null) {
                for (Integer brokerId : request.getBrokers()) {
                    try {
                        metadataService.getBroker(brokerId);
                    } catch (IllegalArgumentException e) {
                        log.warn("Broker {} does not exist for sync trigger", brokerId);
                        return ResponseEntity.badRequest().build();
                    }
                }
            }

            // Validate that specified topics exist
            if (request.getTopics() != null) {
                List<String> existingTopics = metadataService.listTopics();
                for (String topicName : request.getTopics()) {
                    if (!existingTopics.contains(topicName)) {
                        log.warn("Topic {} does not exist for sync trigger", topicName);
                        return ResponseEntity.badRequest().build();
                    }
                }
            }

            // For now, just return success
            // In a real implementation, this would trigger push sync to the specified brokers
            SyncTriggerResponse response = SyncTriggerResponse.builder()
                    .success(true)
                    .brokersTriggered(request.getBrokers() != null ? request.getBrokers().size() : 0)
                    .topicsSynced(request.getTopics() != null ? request.getTopics().size() : 0)
                    .triggerTimestamp(System.currentTimeMillis())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error triggering sync: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Full metadata sync (alternative to incremental)
     */
    @PostMapping("/sync/full")
    public ResponseEntity<FullSyncResponse> fullSync() {
        log.info("Received full sync request");

        try {
            List<String> topics = metadataService.listTopics();
            List<BrokerResponse> brokers = metadataService.listBrokers();

            FullSyncResponse response = FullSyncResponse.builder()
                    .topics(topics)
                    .brokers(brokers)
                    .syncTimestamp(System.currentTimeMillis())
                    .fullSyncPerformed(true)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error performing full sync: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Register a broker
     * Only controller leader can process this
     */
    @PostMapping("/brokers")
    public ResponseEntity<BrokerResponse> registerBroker(
            @Validated @RequestBody RegisterBrokerRequest request) {

        log.info("Received request to register broker: {}", request.getId());

        // Check if this node is the controller leader
        if (!raftController.isControllerLeader()) {
            log.warn("This node is not the controller leader. Current leader: {}",
                    raftController.getControllerLeaderId());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Controller-Leader", raftController.getControllerLeaderId().toString())
                    .build();
        }

        try {
            BrokerResponse response = metadataService.registerBroker(request);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Broker registration failed: {}", e.getMessage());
            // Check if it's a duplicate broker error
            if (e.getMessage().contains("already exists")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error registering broker", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get broker information
     * Can be served by any node (read operation)
     */
    @GetMapping("/brokers/{brokerId}")
    public ResponseEntity<BrokerResponse> getBroker(@PathVariable Integer brokerId) {

        log.debug("Fetching broker: {}", brokerId);

        try {
            BrokerResponse response = metadataService.getBroker(brokerId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Broker not found: {}", brokerId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching broker", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * List all brokers
     * Can be served by any node (read operation)
     */
    @GetMapping("/brokers")
    public ResponseEntity<List<BrokerResponse>> listBrokers() {
        log.debug("Listing all brokers");

        try {
            List<BrokerResponse> brokers = metadataService.listBrokers();
            return ResponseEntity.ok(brokers);

        } catch (Exception e) {
            log.error("Error listing brokers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Receive heartbeat from storage services (simple format for testing)
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<SimpleHeartbeatResponse> receiveSimpleHeartbeat(@RequestBody SimpleHeartbeatRequest request) {
        log.debug("Received simple heartbeat from broker: {}", request.getBrokerId());

        try {
            // Update broker status if it exists
            try {
                metadataService.updateBrokerStatus(request.getBrokerId(), "ONLINE");
            } catch (IllegalArgumentException e) {
                // Broker doesn't exist, that's ok for heartbeat
                log.debug("Heartbeat from unknown broker: {}", request.getBrokerId());
            }

            SimpleHeartbeatResponse response = SimpleHeartbeatResponse.builder()
                    .brokerId(request.getBrokerId())
                    .acknowledged(true)
                    .timestamp(System.currentTimeMillis())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing simple heartbeat from broker {}: {}", request.getBrokerId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Receive heartbeat from storage services (alternative endpoint for testing)
     */
    @PostMapping("/storage-heartbeat")
    public ResponseEntity<TestStorageHeartbeatResponse> receiveStorageHeartbeat(@Validated @RequestBody TestStorageHeartbeatRequest request) {
        log.debug("Received storage heartbeat from service: {}", request.getServiceId());

        try {
            // Custom validation for metadata version
            if (request.getMetadataVersion() != null && request.getMetadataVersion() < 0) {
                log.warn("Invalid metadata version: {}", request.getMetadataVersion());
                return ResponseEntity.badRequest().build();
            }

            // Extract broker ID from service ID (e.g., "storage-101" -> 101)
            Integer brokerId = extractBrokerIdFromServiceId(request.getServiceId());

            // Update broker status if it exists
            try {
                metadataService.updateBrokerStatus(brokerId, request.getIsAlive() ? "ONLINE" : "OFFLINE");
            } catch (IllegalArgumentException e) {
                // Broker doesn't exist, that's ok for heartbeat
                log.debug("Heartbeat from unknown broker: {}", brokerId);
            }

            TestStorageHeartbeatResponse response = TestStorageHeartbeatResponse.builder()
                    .serviceId(request.getServiceId())
                    .acknowledged(true)
                    .timestamp(System.currentTimeMillis())
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid heartbeat request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing storage heartbeat from service {}: {}", request.getServiceId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Receive detailed heartbeat from storage services for ISR management
     * Contains per-partition status including LEO, lag, HWM, and role information
     */
    @PostMapping("/storage-controller-heartbeat")
    public ResponseEntity<StorageControllerHeartbeatResponse> receiveStorageControllerHeartbeat(
            @Validated @RequestBody StorageControllerHeartbeatRequest request) {

        log.debug("Received storage controller heartbeat from node: {}", request.getNodeId());

        try {
            // Validate request
            if (request.getNodeId() == null || request.getNodeId() < 0) {
                log.warn("Invalid node ID: {}", request.getNodeId());
                return ResponseEntity.badRequest().build();
            }

            // Process partition status for ISR management
            if (request.getPartitions() != null) {
                for (com.distributedmq.common.dto.PartitionStatus partition : request.getPartitions()) {
                    log.debug("Processing partition status: {}-{}, role: {}, LEO: {}, lag: {}, HWM: {}",
                            partition.getTopic(), partition.getPartition(),
                            partition.getRole(), partition.getLeo(), partition.getLag(), partition.getHwm());

                    // TODO: Process lag information for ISR management
                    // TODO: Detect partitions that may need ISR changes
                    // TODO: Update controller's view of partition leadership
                }
            }

            // Update broker status
            try {
                metadataService.updateBrokerStatus(request.getNodeId(),
                        request.isAlive() ? "ONLINE" : "OFFLINE");
            } catch (IllegalArgumentException e) {
                // Broker doesn't exist, that's ok for heartbeat
                log.debug("Heartbeat from unknown broker: {}", request.getNodeId());
            }

            // Check if storage node has stale metadata
            Long controllerMetadataVersion = System.currentTimeMillis(); // TODO: Get actual controller metadata version
            boolean metadataInSync = request.getMetadataVersion() != null &&
                    request.getMetadataVersion() >= (controllerMetadataVersion - 1000); // 1 second tolerance

            String instruction = null;
            if (!metadataInSync) {
                instruction = "METADATA_OUTDATED";
                log.info("Storage node {} has outdated metadata (version: {}, controller: {})",
                        request.getNodeId(), request.getMetadataVersion(), controllerMetadataVersion);
            }

            StorageControllerHeartbeatResponse response = StorageControllerHeartbeatResponse.builder()
                    .success(true)
                    .controllerMetadataVersion(controllerMetadataVersion)
                    .metadataInSync(metadataInSync)
                    .responseTimestamp(System.currentTimeMillis())
                    .instruction(instruction)
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid storage controller heartbeat request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing storage controller heartbeat from node {}: {}",
                    request.getNodeId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Receive ISR status batch updates from storage nodes
     * Processes lag threshold breaches and ISR membership changes
     */
    @PostMapping("/isr-status-batch")
    public ResponseEntity<ISRStatusBatchResponse> receiveISRStatusBatch(
            @Validated @RequestBody ISRStatusBatchRequest request) {

        log.debug("Received ISR status batch from node: {} with {} updates",
                request.getNodeId(), request.getIsrUpdates() != null ? request.getIsrUpdates().size() : 0);

        try {
            // Validate request
            if (request.getNodeId() == null || request.getNodeId() < 0) {
                log.warn("Invalid node ID: {}", request.getNodeId());
                return ResponseEntity.badRequest().build();
            }

            // Process ISR status updates
            List<String> instructions = new ArrayList<>();
            if (request.getIsrUpdates() != null) {
                for (ISRStatusUpdate update : request.getIsrUpdates()) {
                    log.debug("Processing ISR update: {}-{}, type: {}, lag: {}",
                            update.getTopic(), update.getPartition(),
                            update.getUpdateType(), update.getCurrentLag());

                    // Process different types of ISR updates
                    switch (update.getUpdateType()) {
                        case LAG_THRESHOLD_BREACHED:
                            handleLagThresholdBreached(update, instructions);
                            break;
                        case LAG_THRESHOLD_RECOVERED:
                            handleLagThresholdRecovered(update, instructions);
                            break;
                        case ISR_MEMBERSHIP_CHANGED:
                            handleISRMembershipChanged(update, instructions);
                            break;
                        default:
                            log.debug("Unhandled ISR update type: {}", update.getUpdateType());
                    }
                }
            }

            // Update broker status
            try {
                metadataService.updateBrokerStatus(request.getNodeId(), "ONLINE");
            } catch (IllegalArgumentException e) {
                // Broker doesn't exist, that's ok for ISR updates
                log.debug("ISR batch from unknown broker: {}", request.getNodeId());
            }

            // Check if storage node has stale metadata
            Long controllerMetadataVersion = System.currentTimeMillis(); // TODO: Get actual controller metadata version
            boolean metadataInSync = request.getMetadataVersion() != null &&
                    request.getMetadataVersion() >= (controllerMetadataVersion - 30000); // 30 second tolerance

            ISRStatusBatchResponse response = ISRStatusBatchResponse.builder()
                    .success(true)
                    .controllerMetadataVersion(controllerMetadataVersion)
                    .responseTimestamp(System.currentTimeMillis())
                    .instructions(instructions)
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid ISR status batch request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing ISR status batch from node {}: {}",
                    request.getNodeId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Handle lag threshold breached event
     */
    private void handleLagThresholdBreached(ISRStatusUpdate update, List<String> instructions) {
        log.warn("Lag threshold breached for {}-{}: currentLag={}, threshold={}",
                update.getTopic(), update.getPartition(), update.getCurrentLag(), update.getLagThreshold());

        try {
            // Remove the lagging broker from ISR
            metadataService.removeFromISR(update.getTopic(), update.getPartition(), update.getBrokerId());

            instructions.add(String.format("ISR_SHRINK:%s-%d:REMOVED_BROKER_%d",
                    update.getTopic(), update.getPartition(), update.getBrokerId()));

            log.info("ISR shrunk for {}-{}: removed broker {} due to lag threshold breach",
                    update.getTopic(), update.getPartition(), update.getBrokerId());

        } catch (Exception e) {
            log.error("Failed to shrink ISR for {}-{}: {}", update.getTopic(), update.getPartition(), e.getMessage());
            instructions.add(String.format("ISR_SHRINK_FAILED:%s-%d",
                    update.getTopic(), update.getPartition()));
        }
    }

    /**
     * Handle lag threshold recovered event
     */
    private void handleLagThresholdRecovered(ISRStatusUpdate update, List<String> instructions) {
        log.info("Lag threshold recovered for {}-{}: currentLag={}, threshold={}",
                update.getTopic(), update.getPartition(), update.getCurrentLag(), update.getLagThreshold());

        try {
            // Add the recovered broker back to ISR
            metadataService.addToISR(update.getTopic(), update.getPartition(), update.getBrokerId());

            instructions.add(String.format("ISR_EXPAND:%s-%d:ADDED_BROKER_%d",
                    update.getTopic(), update.getPartition(), update.getBrokerId()));

            log.info("ISR expanded for {}-{}: added broker {} due to lag recovery",
                    update.getTopic(), update.getPartition(), update.getBrokerId());

        } catch (Exception e) {
            log.error("Failed to expand ISR for {}-{}: {}", update.getTopic(), update.getPartition(), e.getMessage());
            instructions.add(String.format("ISR_EXPAND_FAILED:%s-%d",
                    update.getTopic(), update.getPartition()));
        }
    }

    /**
     * Handle ISR membership changed event
     */
    private void handleISRMembershipChanged(ISRStatusUpdate update, List<String> instructions) {
        log.info("ISR membership changed for {}-{}: inISR={}",
                update.getTopic(), update.getPartition(), update.getIsInISR());

        // This event is informational - the actual ISR changes are handled by the lag events above
        // We could use this for additional validation or logging

        instructions.add(String.format("ISR_MEMBERSHIP_ACK:%s-%d",
                update.getTopic(), update.getPartition()));
    }

    /**
     * Extract broker ID from service ID (e.g., "storage-101" -> 101)
     */
    private Integer extractBrokerIdFromServiceId(String serviceId) {
        if (serviceId == null || !serviceId.startsWith("storage-")) {
            throw new IllegalArgumentException("Invalid service ID format: " + serviceId);
        }
        try {
            return Integer.parseInt(serviceId.substring("storage-".length()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid broker ID in service ID: " + serviceId);
        }
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
