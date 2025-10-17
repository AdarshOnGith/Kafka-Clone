package com.distributedmq.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Metadata about a partition
 * GLOBAL MODEL - Do not rename or delete existing fields!
 * Only ADD new fields for new functionality
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartitionMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    // ORIGINAL FIELDS (DO NOT RENAME/DELETE - used by other services)
    private String topicName;            // Original field name
    private Integer partitionId;         // Original field name
    private BrokerNode leader;
    private List<BrokerNode> replicas;
    private List<BrokerNode> isr;        // Original: List of BrokerNode
    private Long startOffset;
    private Long endOffset;
    
    // NEW FIELDS ADDED (for consumer client library)
    private Long currentOffset;          // Where consumer should start reading
    private Long highWaterMark;          // Latest offset available
    private List<Integer> isrBrokerIds;  // NEW: Broker IDs only (for client convenience)
    
    // Convenience methods for client library compatibility
    public String getTopic() {
        return topicName;
    }
    
    public void setTopic(String topic) {
        this.topicName = topic;
    }
    
    public Integer getPartition() {
        return partitionId;
    }
    
    public void setPartition(Integer partition) {
        this.partitionId = partition;
    }

    @Override
    public String toString() {
        return String.format("PartitionMetadata{topic='%s', partition=%d, leader=%s, currentOffset=%d, highWaterMark=%d}", 
            topicName, partitionId, leader != null ? leader.getAddress() : "null", 
            currentOffset, highWaterMark);
    }
}
