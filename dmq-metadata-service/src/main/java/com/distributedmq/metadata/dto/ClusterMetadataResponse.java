package com.distributedmq.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for full cluster metadata
 * Used by storage services to get complete metadata on startup or refresh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterMetadataResponse {
    
    /**
     * Current metadata version
     * Storage services can use this to detect if their metadata is stale
     */
    private Long version;
    
    /**
     * All brokers in the cluster
     */
    private List<BrokerResponse> brokers;
    
    /**
     * All topics with full partition information (leader, followers, ISR)
     */
    private List<TopicMetadataResponse> topics;
    
    /**
     * Timestamp when this metadata was retrieved
     */
    private Long timestamp;
    
    /**
     * Controller leader information
     */
    private Integer controllerLeaderId;
    
    /**
     * Total number of partitions across all topics
     */
    private Integer totalPartitions;
}
