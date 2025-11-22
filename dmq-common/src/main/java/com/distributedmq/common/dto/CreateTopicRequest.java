package com.distributedmq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to create a new topic
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTopicRequest {
    
    /**
     * Topic name
     */
    private String topicName;
    
    /**
     * Number of partitions
     */
    private Integer partitionCount;
    
    /**
     * Replication factor
     */
    private Integer replicationFactor;
}
