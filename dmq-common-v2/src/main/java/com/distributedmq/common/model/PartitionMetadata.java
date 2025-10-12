package com.distributedmq.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Metadata about a topic partition.
 * Returned by Metadata Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartitionMetadata {
    
    /**
     * Topic name.
     */
    private String topic;
    
    /**
     * Partition ID.
     */
    private int partition;
    
    /**
     * Leader storage node ID.
     */
    private String leaderNodeId;
    
    /**
     * Leader node network address (host:port).
     */
    private String leaderNodeAddress;
    
    /**
     * List of all replica node IDs.
     */
    private List<String> replicas;
    
    /**
     * List of In-Sync Replica (ISR) node IDs.
     */
    private List<String> inSyncReplicas;
    
    /**
     * Current high water mark (latest committed offset).
     */
    private long highWaterMark;
    
    /**
     * Leader epoch (incremented on each leader change).
     */
    private int leaderEpoch;
}
