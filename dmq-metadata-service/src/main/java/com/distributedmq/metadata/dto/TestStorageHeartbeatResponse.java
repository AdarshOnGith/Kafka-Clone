package com.distributedmq.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Test-specific storage heartbeat response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestStorageHeartbeatResponse {

    private String serviceId;
    private Boolean acknowledged;
    private Long timestamp;
}