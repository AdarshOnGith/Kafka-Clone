package com.distributedmq.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata about a partition managed by this storage node.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartitionInfo {
    
    /**
     * Topic name.
     */
    private String topic;
    
    /**
     * Partition number.
     */
    private int partition;
    
    /**
     * Whether this node is the leader for this partition.
     */
    private boolean isLeader;
    
    /**
     * Current leader epoch.
     */
    private int leaderEpoch;
    
    /**
     * Log End Offset (LEO) - next offset to be written.
     */
    private long logEndOffset;
    
    /**
     * High Water Mark (HWM) - highest offset replicated to all ISR.
     */
    private long highWaterMark;
    
    /**
     * Base directory for this partition's data.
     */
    private String dataDirectory;
    
    /**
     * Number of in-sync replicas.
     */
    private int inSyncReplicaCount;
}
