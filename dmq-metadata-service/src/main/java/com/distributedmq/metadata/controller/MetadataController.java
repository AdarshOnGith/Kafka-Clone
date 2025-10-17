package com.distributedmq.metadata.controller;

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
