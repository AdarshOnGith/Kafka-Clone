package com.distributedmq.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for consume (fetch) operations.
 * Contains the fetched messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumeResponse {
    
    /**
     * Topic name.
     */
    private String topic;
    
    /**
     * Partition ID.
     */
    private int partition;
    
    /**
     * List of fetched records.
     */
    private java.util.List<FetchedRecord> records;
    
    /**
     * Next offset to fetch (for client tracking).
     */
    private long nextOffset;
    
    /**
     * High water mark of the partition.
     */
    private long highWaterMark;
    
    /**
     * Represents a fetched record.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchedRecord {
        /**
         * Message key.
         */
        private String key;
        
        /**
         * Message value.
         */
        private String value;
        
        /**
         * Offset of this record.
         */
        private long offset;
        
        /**
         * Timestamp when message was produced.
         */
        private long timestamp;
        
        /**
         * Headers.
         */
        private java.util.Map<String, String> headers;
    }
}
