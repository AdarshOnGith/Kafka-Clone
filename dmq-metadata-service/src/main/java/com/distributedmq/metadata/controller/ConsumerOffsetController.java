package com.distributedmq.metadata.controller;

import com.distributedmq.common.model.ConsumerOffset;
import com.distributedmq.metadata.service.ConsumerOffsetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for consumer offset operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/metadata/offsets")
@RequiredArgsConstructor
public class ConsumerOffsetController {
    
    private final ConsumerOffsetService offsetService;
    
    /**
     * Get consumer offset for a specific partition.
     * GET /api/metadata/offsets/{groupId}/{topic}/{partition}
     */
    @GetMapping("/{groupId}/{topic}/{partition}")
    public ResponseEntity<ConsumerOffset> getConsumerOffset(
            @PathVariable String groupId,
            @PathVariable String topic,
            @PathVariable int partition) {
        
        log.debug("REST: Get consumer offset - group={}, topic={}, partition={}", 
                groupId, topic, partition);
        
        ConsumerOffset offset = offsetService.getConsumerOffset(groupId, topic, partition);
        return ResponseEntity.ok(offset);
    }
    
    /**
     * Get all offsets for a consumer group on a topic.
     * GET /api/metadata/offsets/{groupId}/{topic}
     */
    @GetMapping("/{groupId}/{topic}")
    public ResponseEntity<List<ConsumerOffset>> getConsumerOffsets(
            @PathVariable String groupId,
            @PathVariable String topic) {
        
        log.debug("REST: Get consumer offsets - group={}, topic={}", groupId, topic);
        
        List<ConsumerOffset> offsets = offsetService.getConsumerOffsets(groupId, topic);
        return ResponseEntity.ok(offsets);
    }
    
    /**
     * Commit consumer offset.
     * POST /api/metadata/offsets
     */
    @PostMapping
    public ResponseEntity<Void> commitOffset(@RequestBody ConsumerOffset offset) {
        log.debug("REST: Commit offset - group={}, topic={}, partition={}, offset={}", 
                offset.getGroupId(), offset.getTopicName(), offset.getPartition(), offset.getOffset());
        
        offsetService.commitOffset(offset);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Commit multiple offsets.
     * POST /api/metadata/offsets/batch
     */
    @PostMapping("/batch")
    public ResponseEntity<Void> commitOffsets(@RequestBody List<ConsumerOffset> offsets) {
        log.debug("REST: Commit {} offsets", offsets.size());
        
        offsetService.commitOffsets(offsets);
        return ResponseEntity.ok().build();
    }
}
