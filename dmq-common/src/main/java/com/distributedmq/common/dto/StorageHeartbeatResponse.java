package com.distributedmq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response to storage service heartbeat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageHeartbeatResponse {

    // Whether the heartbeat was processed successfully
    private boolean success;

    // Current controller metadata version
    private Long controllerMetadataVersion;

    // Whether storage service is in sync
    private boolean inSync;

    // Response timestamp
    private Long responseTimestamp;

    // Error message if any
    private String errorMessage;

    // Instructions for the storage service
    private String instruction;
}