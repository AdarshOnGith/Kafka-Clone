package com.distributedmq.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Represents a partition assignment for a consumer with all necessary metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartitionAssignment implements Serializable {
    private static final long serialVersionUID = 1L;

    // Topic and partition info
    private String topic;
    private Integer partition;
    
    // Leader broker info
    private BrokerNode leader;
    
    // Current offset for this consumer group
    private Long currentOffset;
    
    // Partition boundaries
    private Long startOffset;  // Earliest available offset
    private Long endOffset;    // Latest offset (high watermark)
    
    // ISR information (for advanced use cases)
    private Integer replicaCount;
    
    @Override
    public String toString() {
        return String.format("PartitionAssignment{topic='%s', partition=%d, leader=%s, offset=%d}", 
            topic, partition, leader != null ? leader.getAddress() : "null", currentOffset);
    }
}
