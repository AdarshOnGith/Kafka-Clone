package com.distributedmq.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents consumer offset information.
 * Stored in Metadata Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerOffset {
    
    /**
     * Consumer group ID.
     */
    private String groupId;
    
    /**
     * Topic name.
     */
    private String topic;
    
    /**
     * Partition ID.
     */
    private int partition;
    
    /**
     * Committed offset (next offset to consume).
     */
    private long offset;
    
    /**
     * Metadata associated with offset (optional).
     */
    private String metadata;
    
    /**
     * Timestamp when offset was committed.
     */
    private long timestamp;
    
    /**
     * Leader epoch at time of commit (for fencing).
     */
    private int leaderEpoch;
}
