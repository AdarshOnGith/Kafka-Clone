package com.distributedmq.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simple heartbeat request DTO for broker heartbeats
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleHeartbeatRequest {

    private Integer brokerId;
    private Long timestamp;
    private Integer metadataVersion;
}