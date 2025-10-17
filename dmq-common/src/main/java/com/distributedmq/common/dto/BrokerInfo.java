package com.distributedmq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Broker information from CES response
 * Lightweight format with just broker metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokerInfo {
    private Integer id;
    private String host;
    private Integer port;
    private Boolean isAlive;
    private Long lastHeartbeat;
}
