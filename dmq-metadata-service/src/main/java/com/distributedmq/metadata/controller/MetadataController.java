package com.distributedmq.metadata.controller;

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
    public ResponseEntity<TopicMetadataResponse> getTopicMetadata(
            @PathVariable String topicName) {
        
        log.debug("Fetching metadata for topic: {}", topicName);
        
        // TODO: Implement metadata retrieval
        
        return ResponseEntity.ok(new TopicMetadataResponse());
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
    public ResponseEntity<?> getControllerInfo() {
        return ResponseEntity.ok(new ControllerInfo(
                raftController.isControllerLeader(),
                raftController.getControllerLeaderId(),
                raftController.getCurrentTerm()
        ));
    }

    // TODO: Add endpoints for consumer group management
    // TODO: Add endpoints for offset management
    // TODO: Add cluster health endpoints
    
    private record ControllerInfo(boolean isLeader, Integer leaderId, long term) {}
}
