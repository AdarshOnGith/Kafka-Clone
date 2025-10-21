package com.distributedmq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response to storage controller heartbeat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageControllerHeartbeatResponse {

    /**
     * Whether the heartbeat was processed successfully
     */
    private boolean success;

    /**
     * Current controller metadata version
     * Storage node can use this to detect if it has stale metadata
     */
    private Long controllerMetadataVersion;

    /**
     * Whether storage node metadata is in sync
     */
    private boolean metadataInSync;

    /**
     * Response timestamp
     */
    private Long responseTimestamp;

    /**
     * Error message if any
     */
    private String errorMessage;

    /**
     * Instructions for the storage node (e.g., "METADATA_OUTDATED")
     */
    private String instruction;
}