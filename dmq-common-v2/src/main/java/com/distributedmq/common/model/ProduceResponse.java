package com.distributedmq.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response model for produce operations.
 * Returns acknowledgment and offset information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProduceResponse {
    
    /**
     * Topic name.
     */
    private String topic;
    
    /**
     * List of partition results.
     */
    private List<PartitionResult> partitionResults;
    
    /**
     * Overall status code.
     */
    private int statusCode;
    
    /**
     * Error message if any.
     */
    private String errorMessage;
    
    /**
     * Result for a single partition.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartitionResult {
        /**
         * Partition ID.
         */
        private int partition;
        
        /**
         * Base offset of the written batch.
         */
        private long baseOffset;
        
        /**
         * Number of records written.
         */
        private int recordCount;
        
        /**
         * Error message for this partition if any.
         */
        private String error;
    }
}
