package com.distributedmq.storage.wal;

import com.distributedmq.common.exception.ErrorCode;
import com.distributedmq.common.exception.StorageException;
import com.distributedmq.storage.model.LogRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Partition Log - manages all segments for a single partition.
 * Implements the Write-Ahead Log pattern.
 * 
 * Responsibilities:
 * - Append records to the active segment
 * - Roll to new segment when current is full
 * - Read records from appropriate segment
 * - Maintain High Water Mark (HWM)
 * - Clean up old segments based on retention policy
 */
@Slf4j
@Component
public class PartitionLog {
    
    @Value("${dmq.storage.data-dir:./data/storage}")
    private String dataDir;
    
    @Value("${dmq.storage.wal.segment-size-bytes:1073741824}")
    private long segmentSizeBytes;
    
    private final Map<String, LogSegmentManager> partitionLogs = new ConcurrentHashMap<>();
    
    /**
     * Append records to the partition log.
     * Returns the starting offset.
     */
    public long append(String topic, int partition, List<LogRecord> records, int leaderEpoch) {
        String key = getPartitionKey(topic, partition);
        LogSegmentManager manager = partitionLogs.computeIfAbsent(key, 
                k -> new LogSegmentManager(topic, partition, dataDir, segmentSizeBytes));
        
        try {
            // Set leader epoch for all records
            for (LogRecord record : records) {
                record.setLeaderEpoch(leaderEpoch);
                if (record.getTimestamp() == 0) {
                    record.setTimestamp(System.currentTimeMillis());
                }
            }
            
            return manager.append(records);
        } catch (IOException e) {
            log.error("Failed to append records to partition log: topic={}, partition={}", 
                    topic, partition, e);
            throw new StorageException(ErrorCode.WRITE_FAILED, 
                    "Failed to append records", e);
        }
    }
    
    /**
     * Read records from the partition log.
     */
    public List<LogRecord> read(String topic, int partition, long startOffset, int maxRecords) {
        String key = getPartitionKey(topic, partition);
        LogSegmentManager manager = partitionLogs.get(key);
        
        if (manager == null) {
            throw new StorageException(ErrorCode.PARTITION_NOT_FOUND, 
                    String.format("Partition log not found: %s-%d", topic, partition));
        }
        
        try {
            return manager.read(startOffset, maxRecords);
        } catch (IOException e) {
            log.error("Failed to read from partition log: topic={}, partition={}, offset={}", 
                    topic, partition, startOffset, e);
            throw new StorageException(ErrorCode.READ_FAILED, 
                    "Failed to read records", e);
        }
    }
    
    /**
     * Get the Log End Offset (LEO) for a partition.
     */
    public long getLogEndOffset(String topic, int partition) {
        String key = getPartitionKey(topic, partition);
        LogSegmentManager manager = partitionLogs.get(key);
        return manager != null ? manager.getLogEndOffset() : 0L;
    }
    
    /**
     * Get the High Water Mark (HWM) for a partition.
     */
    public long getHighWaterMark(String topic, int partition) {
        String key = getPartitionKey(topic, partition);
        LogSegmentManager manager = partitionLogs.get(key);
        return manager != null ? manager.getHighWaterMark() : 0L;
    }
    
    /**
     * Update the High Water Mark (HWM).
     * Called after replication to all ISR.
     */
    public void updateHighWaterMark(String topic, int partition, long hwm) {
        String key = getPartitionKey(topic, partition);
        LogSegmentManager manager = partitionLogs.get(key);
        if (manager != null) {
            manager.updateHighWaterMark(hwm);
        }
    }
    
    /**
     * Flush all segments for a partition.
     */
    public void flush(String topic, int partition) {
        String key = getPartitionKey(topic, partition);
        LogSegmentManager manager = partitionLogs.get(key);
        if (manager != null) {
            try {
                manager.flush();
            } catch (IOException e) {
                log.error("Failed to flush partition log: topic={}, partition={}", 
                        topic, partition, e);
            }
        }
    }
    
    /**
     * Close all segments for a partition.
     */
    public void close(String topic, int partition) {
        String key = getPartitionKey(topic, partition);
        LogSegmentManager manager = partitionLogs.remove(key);
        if (manager != null) {
            try {
                manager.close();
            } catch (IOException e) {
                log.error("Failed to close partition log: topic={}, partition={}", 
                        topic, partition, e);
            }
        }
    }
    
    private String getPartitionKey(String topic, int partition) {
        return topic + "-" + partition;
    }
    
    /**
     * Internal class to manage segments for a single partition.
     */
    private static class LogSegmentManager {
        private final String topic;
        private final int partition;
        private final String dataDir;
        private final long maxSegmentSize;
        
        private final List<LogSegment> segments = new ArrayList<>();
        private LogSegment activeSegment;
        private long logEndOffset = 0L;
        private long highWaterMark = 0L;
        
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        
        public LogSegmentManager(String topic, int partition, String dataDir, long maxSegmentSize) {
            this.topic = topic;
            this.partition = partition;
            this.dataDir = dataDir;
            this.maxSegmentSize = maxSegmentSize;
            
            // Create first segment
            try {
                activeSegment = new LogSegment(topic, partition, 0L, dataDir, maxSegmentSize);
                segments.add(activeSegment);
            } catch (IOException e) {
                throw new StorageException(ErrorCode.WRITE_FAILED, 
                        "Failed to create initial segment", e);
            }
        }
        
        public long append(List<LogRecord> records) throws IOException {
            lock.writeLock().lock();
            try {
                // Check if we need to roll to a new segment
                if (activeSegment.isFull()) {
                    rollSegment();
                }
                
                long startOffset = activeSegment.append(records);
                logEndOffset = activeSegment.getNextOffset();
                
                return startOffset;
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        public List<LogRecord> read(long startOffset, int maxRecords) throws IOException {
            lock.readLock().lock();
            try {
                // Find the segment containing the start offset
                LogSegment segment = findSegment(startOffset);
                if (segment == null) {
                    return new ArrayList<>();
                }
                
                // Read from the segment
                List<LogRecord> records = segment.read(startOffset, maxRecords);
                
                // Filter out records beyond HWM (consumers only see committed data)
                records.removeIf(record -> record.getOffset() >= highWaterMark);
                
                return records;
            } finally {
                lock.readLock().unlock();
            }
        }
        
        public void flush() throws IOException {
            lock.readLock().lock();
            try {
                activeSegment.flush();
            } finally {
                lock.readLock().unlock();
            }
        }
        
        public void close() throws IOException {
            lock.writeLock().lock();
            try {
                for (LogSegment segment : segments) {
                    segment.close();
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        public long getLogEndOffset() {
            return logEndOffset;
        }
        
        public long getHighWaterMark() {
            return highWaterMark;
        }
        
        public void updateHighWaterMark(long hwm) {
            lock.writeLock().lock();
            try {
                if (hwm > highWaterMark) {
                    highWaterMark = hwm;
                    log.debug("Updated HWM for {}-{}: {}", topic, partition, hwm);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        private void rollSegment() throws IOException {
            log.info("Rolling segment for partition {}-{}, currentSize={}", 
                    topic, partition, activeSegment.size());
            
            // Close current active segment
            activeSegment.close();
            
            // Create new segment
            long newBaseOffset = activeSegment.getNextOffset();
            activeSegment = new LogSegment(topic, partition, newBaseOffset, dataDir, maxSegmentSize);
            segments.add(activeSegment);
        }
        
        private LogSegment findSegment(long offset) {
            for (int i = segments.size() - 1; i >= 0; i--) {
                LogSegment segment = segments.get(i);
                if (offset >= segment.getBaseOffset() && offset < segment.getNextOffset()) {
                    return segment;
                }
            }
            return null;
        }
    }
}
