package com.distributedmq.metadata.controller;

import com.distributedmq.metadata.coordination.RaftController;
import com.distributedmq.metadata.dto.CreateTopicRequest;
import com.distributedmq.metadata.dto.TopicMetadataResponse;
import com.distributedmq.metadata.service.MetadataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * REST Controller for Metadata operations
 * Entry point with request validation
 *
 * When metadata is pushed to storage nodes then storage node should be consistent with 
 * existing operations ( handle it properly different possibilities like, stop all operations update metadata and then start operations again with new data, or let current operations finish and then update metadata for new operations )
 * 
 * Routes requests to controller leader in KRaft mode
 * 
 * Note: Dependencies are optional for Phase 1 testing without database
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/metadata")
public class MetadataController {

    @Autowired(required = false)
    private MetadataService metadataService;
    
    @Autowired(required = false)
    private RaftController raftController;

    /**
     * Create a new topic
     * Only controller leader can process this
     */
    @PostMapping("/topics")
    public ResponseEntity<TopicMetadataResponse> createTopic(
            @Validated @RequestBody CreateTopicRequest request) {
        
        log.info("Received request to create topic: {}", request.getTopicName());
        
        // Phase 1: Service not fully initialized
        if (raftController == null) {
            log.warn("⚠️  Phase 1 mode: RaftController not available (database disabled)");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .build();
        }
        
        // Check if this node is the controller leader
        if (!raftController.isControllerLeader()) {
            log.warn("This node is not the controller leader. Current leader: {}", 
                    raftController.getControllerLeaderId());
            // TODO: Return redirect to leader or forward request
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Controller-Leader", raftController.getControllerLeaderId().toString())
                    .build();
        }
        
        // TODO: Add sanity checks and validation
        // TODO: Submit to Raft log for consensus
        // TODO: Wait for commit
        // TODO: Call metadataService.createTopic(request)
        // TODO: Return proper response
        
        return ResponseEntity.ok(new TopicMetadataResponse());
    }

    /**
     * Get topic metadata
     * Can be served by any node (read operation)
     */
    @GetMapping("/topics/{topicName}")
    public ResponseEntity<com.distributedmq.common.model.TopicMetadata> getTopicMetadata(
            @PathVariable String topicName) {
        
        log.info("📥 Received request for topic metadata: {}", topicName);
        
        // TODO: Implement real metadata retrieval from database
        // For Phase 1: Return mock data
        
        log.info("⚠️  Returning mock metadata (Phase 1 stub)");
        
        // Create broker nodes
        com.distributedmq.common.model.BrokerNode broker1 = com.distributedmq.common.model.BrokerNode.builder()
                .brokerId(1).host("localhost").port(8082).status(com.distributedmq.common.model.BrokerStatus.ONLINE).build();
        com.distributedmq.common.model.BrokerNode broker2 = com.distributedmq.common.model.BrokerNode.builder()
                .brokerId(2).host("localhost").port(8083).status(com.distributedmq.common.model.BrokerStatus.ONLINE).build();
        com.distributedmq.common.model.BrokerNode broker3 = com.distributedmq.common.model.BrokerNode.builder()
                .brokerId(3).host("localhost").port(8084).status(com.distributedmq.common.model.BrokerStatus.ONLINE).build();
        
        List<com.distributedmq.common.model.BrokerNode> allReplicas = Arrays.asList(broker1, broker2, broker3);
        
        // Create mock partition metadata
        List<com.distributedmq.common.model.PartitionMetadata> partitions = new ArrayList<>();
        
        // Partition 0 - Leader: Broker 1
        partitions.add(com.distributedmq.common.model.PartitionMetadata.builder()
                .topicName(topicName)
                .partitionId(0)
                .leader(broker1)
                .replicas(allReplicas)
                .isr(Arrays.asList(broker1, broker2))
                .startOffset(0L)
                .endOffset(100L)
                .build());
        
        // Partition 1 - Leader: Broker 2
        partitions.add(com.distributedmq.common.model.PartitionMetadata.builder()
                .topicName(topicName)
                .partitionId(1)
                .leader(broker2)
                .replicas(allReplicas)
                .isr(Arrays.asList(broker2, broker3))
                .startOffset(0L)
                .endOffset(150L)
                .build());
        
        // Partition 2 - Leader: Broker 3
        partitions.add(com.distributedmq.common.model.PartitionMetadata.builder()
                .topicName(topicName)
                .partitionId(2)
                .leader(broker3)
                .replicas(allReplicas)
                .isr(Arrays.asList(broker3, broker1))
                .startOffset(0L)
                .endOffset(200L)
                .build());
        
        // Build topic metadata
        com.distributedmq.common.model.TopicMetadata metadata = com.distributedmq.common.model.TopicMetadata.builder()
                .topicName(topicName)
                .partitionCount(3)
                .replicationFactor(3)
                .partitions(partitions)
                .createdAt(System.currentTimeMillis())
                .build();
        
        log.info("✅ Returning metadata for topic '{}': {} partitions", topicName, metadata.getPartitionCount());
        
        return ResponseEntity.ok(metadata);
    }

    /**
     * List all topics
     * Can be served by any node (read operation)
     */
    @GetMapping("/topics")
    public ResponseEntity<List<String>> listTopics() {
        log.debug("Listing all topics");
        
        // TODO: Implement topic listing
        
        return ResponseEntity.ok(List.of());
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
        
        // TODO: Submit to Raft log for consensus
        // TODO: Implement topic deletion
        
        return ResponseEntity.noContent().build();
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

    // TODO: Add endpoints for consumer group management
    // TODO: Add endpoints for offset management
    // TODO: Add cluster health endpoints
    
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
