package com.distributedmq.storage.wal;

import com.distributedmq.common.model.Message;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Path;

/**
 * Represents a single log segment file
 */
@Slf4j
public class LogSegment {

    private final Path baseDir;
    private final long baseOffset;
    private final File logFile;
    private final FileOutputStream fileOutputStream;
    private final DataOutputStream dataOutputStream;
    private long currentSize;

    public LogSegment(Path baseDir, long baseOffset) throws IOException {
        this.baseDir = baseDir;
        this.baseOffset = baseOffset;
        this.logFile = new File(baseDir.toFile(), String.format("%020d.log", baseOffset)); // TODO: put in const/config
        
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
        
        // TODO: Implement proper serialization format
        // Format: [size][crc][offset][timestamp][key_length][key][value_length][value]
        
        byte[] serialized = serializeMessage(message);
        
        dataOutputStream.writeInt(serialized.length);
        dataOutputStream.write(serialized);
        
        currentSize += 4 + serialized.length;
    }

    /**
     * Flush to disk
     */
    public void flush() throws IOException {
        dataOutputStream.flush();
        fileOutputStream.getFD().sync();
    }

    /**
     * Close segment
     */
    public void close() throws IOException {
        flush();
        dataOutputStream.close();
        fileOutputStream.close();
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
            dos.writeInt(-1);
        }
        
        // Write value
        dos.writeInt(message.getValue().length);
        dos.write(message.getValue());
        
        return baos.toByteArray();
    }

    // TODO: Add deserialization method
    // TODO: Add CRC checksum
    // TODO: Add compression
}
