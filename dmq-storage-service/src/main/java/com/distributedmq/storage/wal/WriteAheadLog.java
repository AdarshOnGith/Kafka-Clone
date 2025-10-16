package com.distributedmq.storage.wal;

import com.distributedmq.common.model.Message;
import com.distributedmq.storage.config.StorageConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Write-Ahead Log implementation
 * Provides durable message storage with segment-based files
 */
@Slf4j
public class WriteAheadLog {

    private final String topic;
    private final Integer partition;

    // NIO package data type for file and directory paths
    private final Path logDirectory;

    // AtomicLong: a thread-safe long value supporting atomic operations without explicit synchronization mechanisms
    private final AtomicLong nextOffset;
    private final AtomicLong highWaterMark;
    private final AtomicLong logEndOffset; // LEO - Log End Offset

    // LogSegment: a portion of the write-ahead log representing a contiguous range of messages stored on disk
    private LogSegment currentSegment;

    private final StorageConfig config;

    public WriteAheadLog(String topic, Integer partition, StorageConfig config) {
        this.topic = topic;
        this.partition = partition;
        this.config = config;

        this.logDirectory = Paths.get(config.getBrokerLogsDir(), topic, String.valueOf(partition));
        this.nextOffset = new AtomicLong(StorageConfig.DEFAULT_OFFSET);
        this.highWaterMark = new AtomicLong(StorageConfig.DEFAULT_HIGH_WATER_MARK);
        this.logEndOffset = new AtomicLong(StorageConfig.DEFAULT_LOG_END_OFFSET);

        initializeLog();
    }

    private void initializeLog() {
        try {
            Files.createDirectories(logDirectory);
            
            // Recover from existing segments or create new one
            recoverFromExistingSegments();
            
            log.info("Initialized WAL for {}-{} at {} (nextOffset: {}, logEndOffset: {})", 
                    topic, partition, logDirectory, nextOffset.get(), logEndOffset.get());
            
        } catch (IOException e) {
            log.error("Failed to initialize WAL", e);
            throw new RuntimeException("Failed to initialize WAL", e);
        }
    }

    /**
     * Recover WAL state from existing log segments
     */
    private void recoverFromExistingSegments() throws IOException {
        // Find all existing segment files
        File[] segmentFiles = logDirectory.toFile().listFiles((dir, name) -> 
            name.endsWith(StorageConfig.LOG_FILE_EXTENSION));
        
        if (segmentFiles == null || segmentFiles.length == 0) {
            // No existing segments, create new one
            currentSegment = new LogSegment(logDirectory, 0);
            return;
        }
        
        // Find the segment with the highest base offset
        LogSegment latestSegment = null;
        long maxBaseOffset = -1;
        
        for (File segmentFile : segmentFiles) {
            String fileName = segmentFile.getName();
            String baseOffsetStr = fileName.substring(0, fileName.length() - StorageConfig.LOG_FILE_EXTENSION.length());
            long baseOffset = Long.parseLong(baseOffsetStr);
            
            if (baseOffset > maxBaseOffset) {
                maxBaseOffset = baseOffset;
                latestSegment = new LogSegment(logDirectory, baseOffset);
            }
        }
        
        currentSegment = latestSegment;
        
        // Recover offsets by reading the last message from the segment
        long lastOffset = currentSegment.getLastOffset();
        if (lastOffset >= 0) {
            long recoveredOffset = lastOffset + 1;
            nextOffset.set(recoveredOffset);
            logEndOffset.set(recoveredOffset);
            highWaterMark.set(Math.min(highWaterMark.get(), recoveredOffset));
        } else {
            // Empty segment, start from maxBaseOffset
            nextOffset.set(maxBaseOffset);
            logEndOffset.set(maxBaseOffset);
        }
        
        log.info("Recovered WAL state: nextOffset={}, logEndOffset={}, highWaterMark={}", 
                nextOffset.get(), logEndOffset.get(), highWaterMark.get());
    }

    /**
     * Append message to log and return assigned offset
     * Step 2: Assigns offsets to messages atomically
     */
    public long append(Message message) {
        synchronized (this) {
            long offset = nextOffset.getAndIncrement();
            message.setOffset(offset);
            
            try {
                // if max segment size is exceeded; roll to a new segment to keep the log manageable
                if (currentSegment.size() >= config.getWal().getSegmentSizeBytes()) {
                    rollSegment();
                }
                
                currentSegment.append(message);
                
                // Update Log End Offset (LEO)
                logEndOffset.set(offset + 1);
                
                log.debug("Appended message at offset {}", offset);
                
                return offset;
                
            } catch (IOException e) {
                log.error("Failed to append message", e);
                throw new RuntimeException("Failed to append message", e);
            }
        }
    }

    /**
     * Read messages starting from offset
     */
    public List<Message> read(long startOffset, int maxMessages) {
        List<Message> messages = new ArrayList<>();
        
        try {
            // TODO: Implement reading from segments
            // 1. Find correct segment
            // 2. Read messages
            // 3. Deserialize
            
            log.debug("Reading {} messages from offset {}", maxMessages, startOffset);
            
        } catch (Exception e) {
            log.error("Failed to read messages", e);
        }
        
        return messages;
    }

    /**
     * Flush pending writes to disk
     */
    public void flush() {
        try {
            if (currentSegment != null) {
                currentSegment.flush();
            }
        } catch (IOException e) {
            log.error("Failed to flush log", e);
        }
    }

    /**
     * Roll to new segment
     */
    private void rollSegment() throws IOException {
        if (currentSegment != null) {
            currentSegment.close();
        }
        
        currentSegment = new LogSegment(logDirectory, nextOffset.get());
        
        log.info("Rolled to new segment at offset {}", nextOffset.get());
    }

    public long getHighWaterMark() {
        return highWaterMark.get();
    }

    public void updateHighWaterMark(long offset) {
        highWaterMark.set(offset);
    }

    public long getLogEndOffset() {
        return logEndOffset.get();
    }

    public void updateLogEndOffset(long offset) {
        logEndOffset.set(offset);
    }

    // TODO: Add segment cleanup based on retention policy
    // TODO: Add log compaction

    // TODO: Add index for faster lookups
        // Implement an offset-to-position index to enable O(1) seeks within segments.
        // This index maps message offsets to their byte positions in the log file,
        // allowing efficient random access reads without scanning the entire segment.
        // Use a sparse index (e.g., every 4KB or configurable interval) to balance
        // memory usage and lookup speed. Store index entries in memory and persist
        // to disk for recovery.
}
