package com.distributedmq.common.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Heartbeat request sent by metadata services to controller
 * Contains the service's last metadata update timestamp
 */
@Data
@Builder
public class HeartbeatRequest {
    /**
     * ID of the metadata service sending the heartbeat
     */
    private Integer serviceId;

    /**
     * Timestamp of the last metadata update received by this service
     * Used by controller to determine if service is in sync
     */
    private Long lastMetadataUpdateTimestamp;

    /**
     * Current timestamp when heartbeat was sent
     */
    private Long heartbeatTimestamp;
}