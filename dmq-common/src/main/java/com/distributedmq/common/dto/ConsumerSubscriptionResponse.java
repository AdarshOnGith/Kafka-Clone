package com.distributedmq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response containing broker and partition metadata for consumer
 * Returned by Consumer Egress Service
 * 
 * New format: Brokers + Partitions with IDs only
 * Client transforms this to PartitionMetadata with BrokerNode objects
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerSubscriptionResponse {
    private boolean success;
    private String errorMessage;
    
    // Phase 1: Basic fields
    private String groupId;
    private List<BrokerInfo> brokers;      // NEW: Broker metadata
    private List<PartitionInfo> partitions; // CHANGED: Lightweight partition info with IDs only
    private Long timestamp;
    
    // Phase 2+: Future fields for multi-member coordination
    private String consumerId;
    private Integer generationId; // For rebalancing tracking
    private String coordinatorHost;
    private Integer coordinatorPort;
}
