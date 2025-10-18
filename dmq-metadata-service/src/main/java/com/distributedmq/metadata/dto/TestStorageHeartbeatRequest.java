package com.distributedmq.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Test-specific storage heartbeat request DTO that matches test expectations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestStorageHeartbeatRequest {

    @NotBlank(message = "serviceId is required")
    private String serviceId;

    private Long metadataVersion;

    private Integer partitionCount;

    @NotNull(message = "isAlive is required")
    private Boolean isAlive;
}