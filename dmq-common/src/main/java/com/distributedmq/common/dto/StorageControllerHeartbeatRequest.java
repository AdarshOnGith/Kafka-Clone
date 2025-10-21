package com.distributedmq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Heartbeat request from storage service to controller
 * Contains detailed per-partition status for ISR management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageControllerHeartbeatRequest {

    /**
     * Storage node ID (broker ID)
     */
    private Integer nodeId;

    /**
     * Per-partition status array
     */
    private List<PartitionStatus> partitions;

    /**
     * Current metadata version known by this storage node
     */
    private Long metadataVersion;

    /**
     * Heartbeat timestamp
     */
    private Long timestamp;

    /**
     * Service health status
     */
    private boolean alive;
}