package com.distributedmq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Partition information from CES response
 * Lightweight format with broker IDs only (not full BrokerNode objects)
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartitionInfo {
    private String topic;
    private Integer partition;
    private Integer leaderId;
    private List<Integer> followerIds;
    private List<Integer> isrIds;
    private Integer leaderEpoch;
}