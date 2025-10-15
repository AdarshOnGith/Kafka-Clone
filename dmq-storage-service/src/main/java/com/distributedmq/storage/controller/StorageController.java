package com.distributedmq.storage.controller;

import com.distributedmq.common.dto.ProduceRequest;
import com.distributedmq.common.dto.ProduceResponse;
import com.distributedmq.common.dto.ConsumeRequest;
import com.distributedmq.common.dto.ConsumeResponse;
import com.distributedmq.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Storage operations
 * Entry point with request validation
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    /**
     * Append messages to partition (leader only)
     */
    @PostMapping("/produce")
    public ResponseEntity<ProduceResponse> produce(
            @Validated @RequestBody ProduceRequest request) {
        
        log.debug("Received produce request for topic: {}, partition: {}", 
                request.getTopic(), request.getPartition());
        
        // Sanity checks
        if (request.getTopic() == null || request.getTopic().isEmpty()) {
            throw new IllegalArgumentException("Topic cannot be empty");
        }
        
        if (request.getValue() == null || request.getValue().length == 0) {
            throw new IllegalArgumentException("Message value cannot be empty");
        }
        
        ProduceResponse response = storageService.append(request);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Fetch messages from partition
     */
    @PostMapping("/consume")
    public ResponseEntity<ConsumeResponse> consume(
            @Validated @RequestBody ConsumeRequest request) {
        
        log.debug("Received consume request for topic: {}, partition: {}, offset: {}", 
                request.getTopic(), request.getPartition(), request.getOffset());
        
        // Sanity checks
        if (request.getTopic() == null || request.getTopic().isEmpty()) {
            throw new IllegalArgumentException("Topic cannot be empty");
        }
        
        if (request.getOffset() < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }
        
        ConsumeResponse response = storageService.fetch(request);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get partition high water mark
     */
    @GetMapping("/partitions/{topic}/{partition}/high-water-mark")
    public ResponseEntity<Long> getHighWaterMark(
            @PathVariable String topic,
            @PathVariable Integer partition) {
        
        log.debug("Getting high water mark for topic: {}, partition: {}", topic, partition);
        
        Long highWaterMark = storageService.getHighWaterMark(topic, partition);
        
        return ResponseEntity.ok(highWaterMark);
    }

    // TODO: Add replication endpoints
    // TODO: Add partition management endpoints
}
