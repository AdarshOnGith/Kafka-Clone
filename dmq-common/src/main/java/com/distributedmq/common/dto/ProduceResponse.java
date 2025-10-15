package com.distributedmq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response to a produce request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProduceResponse {
    private String topic;
    private Integer partition;
    private Long offset;
    private Long timestamp;
    private String errorMessage;
    private boolean success;

    // TODO: Add error codes
    // TODO: Add throttle time information
}
