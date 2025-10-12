package com.distributedmq.storage.controller;

import com.distributedmq.storage.model.PartitionInfo;
import com.distributedmq.storage.wal.PartitionLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for storage service operations.
 * Provides HTTP endpoints for monitoring and management.
 */
@Slf4j
@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {
    
    private final PartitionLog partitionLog;
    
    @Value("${dmq.storage.node-id}")
    private String nodeId;
    
    /**
     * Get partition status.
     * GET /api/storage/partitions/{topic}/{partition}
     */
    @GetMapping("/partitions/{topic}/{partition}")
    public ResponseEntity<PartitionInfo> getPartitionInfo(
            @PathVariable String topic,
            @PathVariable int partition) {
        
        log.debug("Get partition info: topic={}, partition={}", topic, partition);
        
        long leo = partitionLog.getLogEndOffset(topic, partition);
        long hwm = partitionLog.getHighWaterMark(topic, partition);
        
        PartitionInfo info = PartitionInfo.builder()
                .topic(topic)
                .partition(partition)
                .isLeader(true) // TODO: Get from partition assignment
                .leaderEpoch(0)
                .logEndOffset(leo)
                .highWaterMark(hwm)
                .build();
        
        return ResponseEntity.ok(info);
    }
    
    /**
     * Get node status.
     * GET /api/storage/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getNodeStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("nodeId", nodeId);
        status.put("status", "ALIVE");
        status.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Flush a partition to disk.
     * POST /api/storage/partitions/{topic}/{partition}/flush
     */
    @PostMapping("/partitions/{topic}/{partition}/flush")
    public ResponseEntity<Void> flushPartition(
            @PathVariable String topic,
            @PathVariable int partition) {
        
        log.info("Flush partition: topic={}, partition={}", topic, partition);
        
        partitionLog.flush(topic, partition);
        
        return ResponseEntity.ok().build();
    }
}
