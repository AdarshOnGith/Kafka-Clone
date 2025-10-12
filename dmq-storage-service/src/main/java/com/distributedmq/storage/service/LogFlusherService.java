package com.distributedmq.storage.service;

import com.distributedmq.storage.wal.PartitionLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Log Flusher Service - periodically flushes Write-Ahead Logs to disk.
 * Ensures durability while balancing performance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogFlusherService {
    
    private final PartitionLog partitionLog;
    
    @Value("${dmq.storage.wal.flush-interval-ms:1000}")
    private long flushIntervalMs;
    
    // Track partitions that need flushing
    private final List<PartitionKey> dirtyPartitions = new CopyOnWriteArrayList<>();
    
    /**
     * Mark a partition as dirty (needs flushing).
     */
    public void markDirty(String topic, int partition) {
        PartitionKey key = new PartitionKey(topic, partition);
        if (!dirtyPartitions.contains(key)) {
            dirtyPartitions.add(key);
        }
    }
    
    /**
     * Periodic flush of all dirty partitions.
     */
    @Scheduled(fixedDelayString = "${dmq.storage.wal.flush-interval-ms:1000}")
    public void flushDirtyPartitions() {
        if (dirtyPartitions.isEmpty()) {
            return;
        }
        
        log.debug("Flushing {} dirty partitions", dirtyPartitions.size());
        
        List<PartitionKey> toFlush = new ArrayList<>(dirtyPartitions);
        dirtyPartitions.clear();
        
        for (PartitionKey key : toFlush) {
            try {
                partitionLog.flush(key.topic, key.partition);
            } catch (Exception e) {
                log.error("Failed to flush partition {}-{}", key.topic, key.partition, e);
                // Re-add to dirty list
                dirtyPartitions.add(key);
            }
        }
    }
    
    private static class PartitionKey {
        final String topic;
        final int partition;
        
        PartitionKey(String topic, int partition) {
            this.topic = topic;
            this.partition = partition;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PartitionKey)) return false;
            PartitionKey that = (PartitionKey) o;
            return partition == that.partition && topic.equals(that.topic);
        }
        
        @Override
        public int hashCode() {
            return java.util.Objects.hash(topic, partition);
        }
    }
}
