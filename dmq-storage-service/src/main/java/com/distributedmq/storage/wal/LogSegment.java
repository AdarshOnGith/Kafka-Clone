package com.distributedmq.storage.wal;

import com.distributedmq.common.model.Message;
import com.distributedmq.storage.config.StorageConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.CRC32;

/**
 * Represents a single log segment file
 */
@Slf4j
public class LogSegment implements AutoCloseable {

    private final Path baseDir;
    private final long baseOffset;
    private final File logFile;
    private FileOutputStream fileOutputStream;
    private DataOutputStream dataOutputStream;
    private long currentSize;
    private static final int INT_BYTES = Integer.BYTES; // always 4 bytes in java

    // Read-write lock for concurrent access: multiple readers, single writer
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public LogSegment(Path baseDir, long baseOffset) throws IOException {
        this.baseDir = baseDir;
        this.baseOffset = baseOffset;
        this.logFile = new File(baseDir.toFile(), String.format(StorageConfig.LOG_FILE_FORMAT, baseOffset));

        // Create file if it doesn't exist
        logFile.getParentFile().mkdirs();
        logFile.createNewFile();

        this.fileOutputStream = new FileOutputStream(logFile, true);
        this.dataOutputStream = new DataOutputStream(fileOutputStream);
        this.currentSize = logFile.length();

        log.debug("Created log segment: {}", logFile.getPath());
    }

    /**
     * Append message to segment
     */
    public void append(Message message) throws IOException {
        rwLock.writeLock().lock();
        try {
            // Format: [size][crc][offset][timestamp][key_length][key][value_length][value]
            
            byte[] serialized = serializeMessage(message);
            CRC32 crc = new CRC32();
            crc.update(serialized);
            int checksum = (int) crc.getValue();
            
            dataOutputStream.writeInt(serialized.length + 4); // size includes CRC
            dataOutputStream.writeInt(checksum);
            dataOutputStream.write(serialized);
            
            currentSize += Integer.BYTES + Integer.BYTES + serialized.length;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Flush to disk
     */
    public void flush() throws IOException {
        rwLock.writeLock().lock();
        try {
            dataOutputStream.flush();
            fileOutputStream.getFD().sync();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Close segment
     */
    @Override
    public void close() throws IOException {
        rwLock.writeLock().lock();
        try {
            closeOutputStreams();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public long size() {
        return currentSize;
    }

    /**
     * Serialize message to bytes
     */
    private byte[] serializeMessage(Message message) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // Write offset
        dos.writeLong(message.getOffset());
        
        // Write timestamp
        dos.writeLong(message.getTimestamp());
        
        // Write key
        if (message.getKey() != null) {
            byte[] keyBytes = message.getKey().getBytes();
            dos.writeInt(keyBytes.length);
            dos.write(keyBytes);
        } else {
            dos.writeInt(StorageConfig.NULL_KEY_LENGTH);
        }
        
        // Write value
        dos.writeInt(message.getValue().length);
        dos.write(message.getValue());
        
        return baos.toByteArray();
    }

    /**
     * Deserialize message from bytes
     */
    private Message deserializeMessage(byte[] data) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        
        // Read offset
        long offset = dis.readLong();
        
        // Read timestamp
        long timestamp = dis.readLong();
        
        // Read key
        int keyLength = dis.readInt();
        String key = null;
        if (keyLength != StorageConfig.NULL_KEY_LENGTH) {
            byte[] keyBytes = new byte[keyLength];
            dis.readFully(keyBytes);
            key = new String(keyBytes);
        }
        
        // Read value
        int valueLength = dis.readInt();
        byte[] value = new byte[valueLength];
        dis.readFully(value);
        
        return Message.builder()
                .offset(offset)
                .timestamp(timestamp)
                .key(key)
                .value(value)
                .build();
    }

    /**
     * Read the last message from the segment to get the last offset
     */
    public long getLastOffset() throws IOException {
        rwLock.readLock().lock();
        try {
            if (currentSize == 0) {
                return -1; // No messages
            }
            
            try (FileInputStream fis = new FileInputStream(logFile);
                 DataInputStream dis = new DataInputStream(fis)) {
                
                long lastOffset = -1;
                while (dis.available() > 0) {
                    int totalLength = dis.readInt();
                    int expectedChecksum = dis.readInt();
                    
                    byte[] messageData = new byte[totalLength - 4]; // exclude CRC
                    dis.readFully(messageData);
                    
                    // Validate checksum
                    CRC32 crc = new CRC32();
                    crc.update(messageData);
                    int actualChecksum = (int) crc.getValue();
                    
                    if (expectedChecksum != actualChecksum) {
                        log.error("Checksum validation failed for message at position in segment {}", logFile.getName());
                        throw new IOException("Corrupted message detected in segment " + logFile.getName());
                    }
                    
                    Message message = deserializeMessage(messageData);
                    lastOffset = message.getOffset();
                }
                
                return lastOffset;
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Read messages from this segment starting from the given offset
     */
    public List<Message> readFromOffset(long startOffset, int maxMessages) throws IOException {
        rwLock.readLock().lock();
        try {
            List<Message> messages = new ArrayList<>();
            
            if (currentSize == 0) {
                return messages;
            }
            
            try (FileInputStream fis = new FileInputStream(logFile);
                 DataInputStream dis = new DataInputStream(fis)) {
                
                while (dis.available() > 0 && messages.size() < maxMessages) {
                    int totalLength = dis.readInt();
                    int expectedChecksum = dis.readInt();
                    
                    byte[] messageData = new byte[totalLength - 4]; // exclude CRC
                    dis.readFully(messageData);
                    
                    // Validate checksum
                    CRC32 crc = new CRC32();
                    crc.update(messageData);
                    int actualChecksum = (int) crc.getValue();
                    
                    if (expectedChecksum != actualChecksum) {
                        log.error("Checksum validation failed for message at offset {} in segment {}", 
                                 startOffset, logFile.getName());
                        throw new IOException("Corrupted message detected in segment " + logFile.getName());
                    }
                    
                    Message message = deserializeMessage(messageData);
                    
                    // Only include messages at or after the start offset
                    if (message.getOffset() >= startOffset) {
                        messages.add(message);
                    }
                }
            }
            
            return messages;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get the base offset of this segment
     */
    public long getBaseOffset() {
        return baseOffset;
    }

    /**
     * Close output streams to allow reading
     */
    private void closeOutputStreams() throws IOException {
        if (dataOutputStream != null) {
            try {
                dataOutputStream.close();
            } catch (IOException e) {
                log.warn("Failed to close data output stream", e);
            } finally {
                dataOutputStream = null;
            }
        }
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                log.warn("Failed to close file output stream", e);
            } finally {
                fileOutputStream = null;
            }
        }
    }

    // TODO: Add CRC checksum
    // TODO: Add compression
}
