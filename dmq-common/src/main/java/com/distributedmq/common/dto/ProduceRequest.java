package com.distributedmq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to produce a message
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProduceRequest {
    private String topic;
    private String key;
    private byte[] value;
    private Integer partition; // Optional, for explicit partition assignment
    private Integer requiredAcks; // 0, 1, or -1 (all)
    private Long timeoutMs;

    // TODO: Add batch support
    // TODO: Add transactional support
}
