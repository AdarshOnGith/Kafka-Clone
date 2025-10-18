package com.distributedmq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Heartbeat request from storage service to controller
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageHeartbeatRequest {

    // Storage service ID
    private Integer storageServiceId;

    // Current metadata version known by this storage service
    private Long currentMetadataVersion;

    // Timestamp of last metadata update received
    private Long lastMetadataUpdateTimestamp;

    // Heartbeat timestamp
    private Long heartbeatTimestamp;

    // Service status
    private boolean alive;

    // Number of partitions this service is leading
    private Integer partitionsLeading;

    // Number of partitions this service is following
    private Integer partitionsFollowing;
}