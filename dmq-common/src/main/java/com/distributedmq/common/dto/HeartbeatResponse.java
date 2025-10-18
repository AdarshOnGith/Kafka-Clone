package com.distributedmq.common.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Heartbeat response sent by controller to metadata services
 * Contains sync status and any required actions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatResponse {
    /**
     * Whether the heartbeat was processed successfully
     */
    private boolean success;

    /**
     * Whether this service is considered in sync with controller
     */
    private boolean inSync;

    /**
     * Controller's current metadata timestamp (truth value)
     */
    private Long controllerMetadataTimestamp;

    /**
     * Error message if heartbeat processing failed
     */
    private String errorMessage;

    /**
     * Timestamp when response was generated
     */
    private Long responseTimestamp;
}