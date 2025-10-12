package com.distributedmq.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request model for producing messages.
 * Represents the payload sent from Producer Client to API Gateway.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProduceRequest {
    
    /**
     * List of records to be produced.
     */
    private List<Record> records;
    
    /**
     * Represents a single message record.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Record {
        /**
         * Optional key for partitioning.
         * If null, sticky partitioning is used.
         */
        private String key;
        
        /**
         * Message payload (JSON or binary).
         */
        private String value;
        
        /**
         * Optional timestamp. If not provided, server assigns current time.
         */
        private Long timestamp;
        
        /**
         * Optional headers for metadata.
         */
        private java.util.Map<String, String> headers;
    }
}
