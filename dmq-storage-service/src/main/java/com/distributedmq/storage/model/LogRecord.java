package com.distributedmq.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Internal representation of a log record.
 * This is written to the Write-Ahead Log.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogRecord {
    
    /**
     * Unique offset for this record within the partition.
     */
    private long offset;
    
    /**
     * Timestamp when the record was produced.
     */
    private long timestamp;
    
    /**
     * Message key (nullable).
     */
    private String key;
    
    /**
     * Message value (payload).
     */
    private byte[] value;
    
    /**
     * Optional headers.
     */
    private Map<String, String> headers;
    
    /**
     * Size of the serialized record in bytes.
     */
    private int size;
    
    /**
     * CRC32 checksum for integrity verification.
     */
    private long crc;
    
    /**
     * Leader epoch when this record was written.
     */
    private int leaderEpoch;
}
