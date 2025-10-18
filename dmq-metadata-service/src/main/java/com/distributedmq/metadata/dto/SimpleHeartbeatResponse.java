package com.distributedmq.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simple heartbeat response DTO for broker heartbeats
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleHeartbeatResponse {

    private Integer brokerId;
    private boolean acknowledged;
    private Long timestamp;
}