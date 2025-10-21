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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Write-Ahead Log implementation
 * Provides durable message storage with segment-based files
 */
@Slf4j
public class WriteAheadLog implements AutoCloseable {

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
        
        // Sort segments by base offset
        List<LogSegment> segments = new ArrayList<>();
        for (File segmentFile : segmentFiles) {
            String fileName = segmentFile.getName();
            String baseOffsetStr = fileName.substring(0, fileName.length() - StorageConfig.LOG_FILE_EXTENSION.length());
            try {
                long baseOffset = Long.parseLong(baseOffsetStr);
                segments.add(new LogSegment(logDirectory, baseOffset));
            } catch (NumberFormatException e) {
                log.warn("Skipping invalid segment file: {}", fileName);
            }
        }
        
        // Sort segments by base offset
        segments.sort(Comparator.comparingLong(LogSegment::getBaseOffset));
        
        // Validate all segments for consistency
        validateSegmentsConsistency(segments);
        
        // Find the latest valid segment
        LogSegment latestSegment = segments.get(segments.size() - 1);
        currentSegment = latestSegment;
        
        // Recover offsets by validating the entire log
        long recoveredNextOffset = recoverOffsetsFromValidatedSegments(segments);
        
        nextOffset.set(recoveredNextOffset);
        logEndOffset.set(recoveredNextOffset);
        highWaterMark.set(Math.min(highWaterMark.get(), recoveredNextOffset));
        
        log.info("Recovered WAL state: nextOffset={}, logEndOffset={}, highWaterMark={}, segments={}", 
                nextOffset.get(), logEndOffset.get(), highWaterMark.get(), segments.size());
    }

    /**
     * Validate consistency of all log segments
     */
    private void validateSegmentsConsistency(List<LogSegment> segments) throws IOException {
        if (segments.isEmpty()) {
            return;
        }
        
        long expectedBaseOffset = 0;
        long previousLastOffset = -1;
        
        for (int i = 0; i < segments.size(); i++) {
            LogSegment segment = segments.get(i);
            long segmentBaseOffset = segment.getBaseOffset();
            
            // Validate base offset continuity
            if (i == 0) {
                if (segmentBaseOffset != 0) {
                    log.warn("First segment base offset is not 0: {}", segmentBaseOffset);
                }
            } else {
                if (segmentBaseOffset != expectedBaseOffset) {
                    log.error("Segment base offset discontinuity: expected {}, found {}", 
                             expectedBaseOffset, segmentBaseOffset);
                    throw new IOException("Inconsistent segment base offsets in WAL recovery");
                }
            }
            
            // Validate segment content and get last offset
            long lastOffset = segment.getLastOffset();
            if (lastOffset >= 0) {
                // Validate offset continuity within segment
                if (lastOffset < segmentBaseOffset) {
                    log.error("Segment last offset {} is less than base offset {}", 
                             lastOffset, segmentBaseOffset);
                    throw new IOException("Invalid offset range in segment");
                }
                
                // Validate continuity between segments
                if (previousLastOffset >= 0 && segmentBaseOffset != (previousLastOffset + 1)) {
                    log.error("Segment continuity broken: previous last offset {}, current base offset {}", 
                             previousLastOffset, segmentBaseOffset);
                    throw new IOException("Broken continuity between segments");
                }
                
                previousLastOffset = lastOffset;
                expectedBaseOffset = lastOffset + 1;
            } else {
                // Empty segment
                expectedBaseOffset = segmentBaseOffset;
            }
            
            log.debug("Validated segment {}: baseOffset={}, lastOffset={}", 
                     segmentBaseOffset, segmentBaseOffset, lastOffset);
        }
        
        log.info("Successfully validated {} segments for consistency", segments.size());
    }

    /**
     * Recover next offset from validated segments
     */
    private long recoverOffsetsFromValidatedSegments(List<LogSegment> segments) throws IOException {
        if (segments.isEmpty()) {
            return 0;
        }
        
        // Start from the last segment
        LogSegment lastSegment = segments.get(segments.size() - 1);
        long lastOffset = lastSegment.getLastOffset();
        
        if (lastOffset >= 0) {
            return lastOffset + 1;
        } else {
            // Empty last segment, use its base offset
            return lastSegment.getBaseOffset();
        }
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
     * TODO: Add isolation level support (read_committed vs read_uncommitted)
     * Currently only supports read_committed (filters by HWM)
     */
    public List<Message> read(long startOffset, int maxMessages) {
        List<Message> messages = new ArrayList<>();
        
        try {
            // For now, read from the current segment only
            // TODO: Implement multi-segment reading
            if (currentSegment != null) {
                List<Message> segmentMessages = currentSegment.readFromOffset(startOffset, maxMessages);
                
                // Filter messages that are within bounds (after startOffset and before highWaterMark)
                for (Message message : segmentMessages) {
                    if (message.getOffset() >= startOffset && message.getOffset() < highWaterMark.get()) {
                        messages.add(message);
                        if (messages.size() >= maxMessages) {
                            break;
                        }
                    }
                }
            }
            
            log.debug("Read {} messages starting from offset {}", messages.size(), startOffset);
            
        } catch (Exception e) {
            log.error("Failed to read messages from offset {}", startOffset, e);
        }
        
        return messages;
    }

    /**
     * Find all log segments for this partition
     */
    private List<LogSegment> findSegmentsForPartition() throws IOException {
        List<LogSegment> segments = new ArrayList<>();
        
        // Check if log directory exists
        if (!Files.exists(logDirectory)) {
            return segments;
        }
        
        // Find all .log files in the partition directory
        File[] segmentFiles = logDirectory.toFile().listFiles((dir, name) -> 
            name.endsWith(StorageConfig.LOG_FILE_EXTENSION));
        
        if (segmentFiles != null) {
            for (File segmentFile : segmentFiles) {
                String fileName = segmentFile.getName();
                String baseOffsetStr = fileName.substring(0, fileName.length() - StorageConfig.LOG_FILE_EXTENSION.length());
                try {
                    long baseOffset = Long.parseLong(baseOffsetStr);
                    segments.add(new LogSegment(logDirectory, baseOffset));
                } catch (NumberFormatException e) {
                    log.warn("Invalid segment file name: {}", fileName);
                }
            }
        }
        
        return segments;
    }

    /**
     * Find the segment that contains the given offset
     */
    private LogSegment findSegmentContainingOffset(List<LogSegment> segments, long offset) {
        if (segments.isEmpty()) {
            return null;
        }
        
        // Segments are sorted by base offset
        for (int i = segments.size() - 1; i >= 0; i--) {
            LogSegment segment = segments.get(i);
            long baseOffset = segment.getBaseOffset();
            
            // Check if this segment could contain the offset
            if (baseOffset <= offset) {
                // For the last segment, it can contain any offset >= baseOffset
                if (i == segments.size() - 1) {
                    return segment;
                }
                
                // For other segments, check if offset is before next segment's base offset
                long nextBaseOffset = segments.get(i + 1).getBaseOffset();
                if (offset < nextBaseOffset) {
                    return segment;
                }
            }
        }
        
        return null;
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

    /**
     * Close the WAL and release resources
     */
    @Override
    public void close() throws IOException {
        if (currentSegment != null) {
            currentSegment.close();
        }
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
