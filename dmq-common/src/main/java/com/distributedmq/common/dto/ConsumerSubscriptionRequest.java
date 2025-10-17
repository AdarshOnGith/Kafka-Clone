package com.distributedmq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to join consumer group and get partition metadata
 * Sent to Consumer Egress Service
 * 
 * Phase 1: Single topic subscription only
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerSubscriptionRequest {
    // Required fields (Phase 1)
    private String groupId;
    private String consumerId;
    private String topic;  // Changed: Single topic only (not List)
    
    // Optional fields for future use (Phase 2+)
    private String clientId;
    private Long sessionTimeoutMs;
    private Long heartbeatIntervalMs;
    private String autoOffsetReset; // earliest, latest, none - for future client-side logic
}
