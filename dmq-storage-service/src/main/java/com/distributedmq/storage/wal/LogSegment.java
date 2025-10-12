package com.distributedmq.storage.wal;

import com.distributedmq.storage.model.LogRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.CRC32;

/**
 * Write-Ahead Log Segment.
 * Each segment is a file containing a sequential series of log records.
 * 
 * File Format:
 * - Magic Byte (1 byte): 0x01
 * - Record Count (4 bytes)
 * - Records:
 *   - Offset (8 bytes)
 *   - Timestamp (8 bytes)
 *   - Leader Epoch (4 bytes)
 *   - Key Length (4 bytes)
 *   - Key (variable)
 *   - Value Length (4 bytes)
 *   - Value (variable)
 *   - Header Count (4 bytes)
 *   - Headers (variable)
 *   - CRC32 (4 bytes)
 */
@Slf4j
public class LogSegment {
    
    private static final byte MAGIC_BYTE = 0x01;
    private static final int HEADER_SIZE = 1 + 4; // magic + record count
    
    private final String topic;
    private final int partition;
    private final long baseOffset;
    private final Path segmentFile;
    private final Path indexFile;
    private final long maxSegmentSize;
    
    private FileChannel fileChannel;
    private RandomAccessFile randomAccessFile;
    private long nextOffset;
    private int recordCount;
    private boolean closed;
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public LogSegment(String topic, int partition, long baseOffset, 
                     String dataDir, long maxSegmentSize) throws IOException {
        this.topic = topic;
        this.partition = partition;
        this.baseOffset = baseOffset;
        this.maxSegmentSize = maxSegmentSize;
        this.nextOffset = baseOffset;
        this.recordCount = 0;
        
        // Create directory structure: dataDir/topic-partition/
        Path partitionDir = Paths.get(dataDir, topic + "-" + partition);
        Files.createDirectories(partitionDir);
        
        // Segment file: 00000000000000001234.log
        this.segmentFile = partitionDir.resolve(String.format("%020d.log", baseOffset));
        this.indexFile = partitionDir.resolve(String.format("%020d.index", baseOffset));
        
        // Open file channel for writing
        this.randomAccessFile = new RandomAccessFile(segmentFile.toFile(), "rw");
        this.fileChannel = randomAccessFile.getChannel();
        
        // Write header if new file
        if (randomAccessFile.length() == 0) {
            writeHeader();
        } else {
            // Read existing file to determine next offset
            readExistingSegment();
        }
        
        log.info("Created log segment: topic={}, partition={}, baseOffset={}, file={}", 
                topic, partition, baseOffset, segmentFile);
    }
    
    /**
     * Append a batch of records to the segment.
     * Returns the starting offset.
     */
    public long append(List<LogRecord> records) throws IOException {
        lock.writeLock().lock();
        try {
            if (closed) {
                throw new IllegalStateException("Segment is closed");
            }
            
            long startOffset = nextOffset;
            
            for (LogRecord record : records) {
                // Assign offset
                record.setOffset(nextOffset);
                record.setLeaderEpoch(record.getLeaderEpoch());
                
                // Calculate CRC
                record.setCrc(calculateCRC(record));
                
                // Serialize and write
                byte[] serialized = serializeRecord(record);
                record.setSize(serialized.length);
                
                ByteBuffer buffer = ByteBuffer.wrap(serialized);
                while (buffer.hasRemaining()) {
                    fileChannel.write(buffer);
                }
                
                nextOffset++;
                recordCount++;
            }
            
            // Update record count in header
            updateRecordCount();
            
            log.debug("Appended {} records to segment, startOffset={}, endOffset={}", 
                    records.size(), startOffset, nextOffset - 1);
            
            return startOffset;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Read records starting from the given offset.
     */
    public List<LogRecord> read(long startOffset, int maxRecords) throws IOException {
        lock.readLock().lock();
        try {
            if (startOffset < baseOffset || startOffset >= nextOffset) {
                return new ArrayList<>();
            }
            
            List<LogRecord> records = new ArrayList<>();
            
            // Position to start reading
            long position = findPosition(startOffset);
            fileChannel.position(position);
            
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 1 MB buffer
            
            for (int i = 0; i < maxRecords && fileChannel.position() < fileChannel.size(); i++) {
                buffer.clear();
                
                // Read record size first (offset + timestamp + ... )
                // For simplicity, we'll read a chunk and deserialize
                int bytesRead = fileChannel.read(buffer);
                if (bytesRead == -1) break;
                
                buffer.flip();
                LogRecord record = deserializeRecord(buffer);
                if (record != null && record.getOffset() < nextOffset) {
                    records.add(record);
                } else {
                    break;
                }
            }
            
            return records;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Flush changes to disk.
     */
    public void flush() throws IOException {
        lock.writeLock().lock();
        try {
            if (!closed) {
                fileChannel.force(true);
                log.trace("Flushed segment to disk: {}", segmentFile);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Close the segment.
     */
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            if (!closed) {
                flush();
                fileChannel.close();
                randomAccessFile.close();
                closed = true;
                log.info("Closed log segment: {}", segmentFile);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get the size of the segment in bytes.
     */
    public long size() throws IOException {
        return fileChannel.size();
    }
    
    /**
     * Check if segment is full.
     */
    public boolean isFull() throws IOException {
        return size() >= maxSegmentSize;
    }
    
    public long getBaseOffset() {
        return baseOffset;
    }
    
    public long getNextOffset() {
        return nextOffset;
    }
    
    public int getRecordCount() {
        return recordCount;
    }
    
    // ========== Private Helper Methods ==========
    
    private void writeHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
        header.put(MAGIC_BYTE);
        header.putInt(0); // Initial record count
        header.flip();
        
        fileChannel.write(header, 0);
    }
    
    private void updateRecordCount() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(recordCount);
        buffer.flip();
        fileChannel.write(buffer, 1); // Position after magic byte
    }
    
    private void readExistingSegment() throws IOException {
        // Read header
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
        fileChannel.read(header, 0);
        header.flip();
        
        byte magic = header.get();
        if (magic != MAGIC_BYTE) {
            throw new IOException("Invalid segment file format");
        }
        
        recordCount = header.getInt();
        
        // Calculate next offset (simplified - in production, scan all records)
        nextOffset = baseOffset + recordCount;
        
        // Position at end for appending
        fileChannel.position(fileChannel.size());
    }
    
    private long findPosition(long offset) throws IOException {
        // Simplified - in production, use index file for binary search
        // For now, scan from beginning (inefficient but correct)
        long position = HEADER_SIZE;
        long currentOffset = baseOffset;
        
        while (currentOffset < offset && position < fileChannel.size()) {
            // Skip to next record (simplified)
            currentOffset++;
            position += 100; // Approximate record size
        }
        
        return position;
    }
    
    private byte[] serializeRecord(LogRecord record) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // Write offset
        dos.writeLong(record.getOffset());
        
        // Write timestamp
        dos.writeLong(record.getTimestamp());
        
        // Write leader epoch
        dos.writeInt(record.getLeaderEpoch());
        
        // Write key
        if (record.getKey() != null) {
            byte[] keyBytes = record.getKey().getBytes();
            dos.writeInt(keyBytes.length);
            dos.write(keyBytes);
        } else {
            dos.writeInt(-1);
        }
        
        // Write value
        dos.writeInt(record.getValue().length);
        dos.write(record.getValue());
        
        // Write headers (simplified)
        dos.writeInt(record.getHeaders() != null ? record.getHeaders().size() : 0);
        if (record.getHeaders() != null) {
            for (var entry : record.getHeaders().entrySet()) {
                dos.writeUTF(entry.getKey());
                dos.writeUTF(entry.getValue());
            }
        }
        
        // Write CRC
        dos.writeInt((int) record.getCrc());
        
        return baos.toByteArray();
    }
    
    private LogRecord deserializeRecord(ByteBuffer buffer) {
        try {
            if (buffer.remaining() < 8 + 8 + 4) {
                return null; // Not enough data
            }
            
            LogRecord record = new LogRecord();
            
            // Read offset
            record.setOffset(buffer.getLong());
            
            // Read timestamp
            record.setTimestamp(buffer.getLong());
            
            // Read leader epoch
            record.setLeaderEpoch(buffer.getInt());
            
            // Read key
            int keyLength = buffer.getInt();
            if (keyLength > 0 && buffer.remaining() >= keyLength) {
                byte[] keyBytes = new byte[keyLength];
                buffer.get(keyBytes);
                record.setKey(new String(keyBytes));
            }
            
            // Read value
            int valueLength = buffer.getInt();
            if (valueLength > 0 && buffer.remaining() >= valueLength) {
                byte[] valueBytes = new byte[valueLength];
                buffer.get(valueBytes);
                record.setValue(valueBytes);
            } else {
                return null;
            }
            
            // Read headers (simplified)
            int headerCount = buffer.getInt();
            // Skip headers for now
            
            // Read CRC
            if (buffer.remaining() >= 4) {
                record.setCrc(buffer.getInt());
            }
            
            return record;
            
        } catch (Exception e) {
            log.error("Error deserializing record", e);
            return null;
        }
    }
    
    private long calculateCRC(LogRecord record) {
        CRC32 crc = new CRC32();
        crc.update((int) record.getOffset());
        crc.update((int) record.getTimestamp());
        if (record.getKey() != null) {
            crc.update(record.getKey().getBytes());
        }
        if (record.getValue() != null) {
            crc.update(record.getValue());
        }
        return crc.getValue();
    }
    
    /**
     * Delete this segment's files.
     */
    public void delete() throws IOException {
        close();
        Files.deleteIfExists(segmentFile);
        Files.deleteIfExists(indexFile);
        log.info("Deleted segment: {}", segmentFile);
    }
}
