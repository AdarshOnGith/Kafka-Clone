package com.distributedmq.metadata.controller;

import com.distributedmq.common.model.PartitionMetadata;
import com.distributedmq.metadata.service.PartitionMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for partition metadata operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/metadata/partitions")
@RequiredArgsConstructor
public class PartitionMetadataController {
    
    private final PartitionMetadataService metadataService;
    
    /**
     * Get partition metadata.
     * GET /api/metadata/partitions/{topic}/{partition}
     */
    @GetMapping("/{topic}/{partition}")
    public ResponseEntity<PartitionMetadata> getPartitionMetadata(
            @PathVariable String topic,
            @PathVariable int partition) {
        
        log.debug("REST: Get partition metadata - topic={}, partition={}", topic, partition);
        
        PartitionMetadata metadata = metadataService.getPartitionMetadata(topic, partition);
        return ResponseEntity.ok(metadata);
    }
    
    /**
     * Get all partitions for a topic.
     * GET /api/metadata/partitions/{topic}
     */
    @GetMapping("/{topic}")
    public ResponseEntity<List<PartitionMetadata>> getTopicMetadata(@PathVariable String topic) {
        log.debug("REST: Get topic metadata - topic={}", topic);
        
        List<PartitionMetadata> metadata = metadataService.getTopicMetadata(topic);
        return ResponseEntity.ok(metadata);
    }
    
    /**
     * Update partition leader (called by Controller Service).
     * PUT /api/metadata/partitions/{topic}/{partition}/leader
     */
    @PutMapping("/{topic}/{partition}/leader")
    public ResponseEntity<Void> updatePartitionLeader(
            @PathVariable String topic,
            @PathVariable int partition,
            @RequestBody UpdateLeaderRequest request) {
        
        log.info("REST: Update partition leader - topic={}, partition={}, newLeader={}", 
                topic, partition, request.getNewLeaderNodeId());
        
        metadataService.updatePartitionLeader(
                topic, 
                partition, 
                request.getNewLeaderNodeId(),
                request.getNewLeaderAddress(),
                request.getNewEpoch()
        );
        
        return ResponseEntity.ok().build();
    }
    
    @lombok.Data
    public static class UpdateLeaderRequest {
        private String newLeaderNodeId;
        private String newLeaderAddress;
        private int newEpoch;
    }
}
