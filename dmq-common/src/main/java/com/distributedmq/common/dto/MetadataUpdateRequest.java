package com.distributedmq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request to update metadata in storage nodes
 * Sent by metadata service to storage nodes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataUpdateRequest {

    // Metadata version for ordering and conflict resolution
    private Long version;

    // Broker information updates
    private List<BrokerInfo> brokers;

    // Partition leadership updates
    private List<PartitionMetadata> partitions;

    // Timestamp of this metadata update
    private Long timestamp;

    /**
     * Broker information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerInfo {
        private Integer id;
        private String host;
        private Integer port;
        private boolean isAlive;
        private Long lastHeartbeat;
    }

    /**
     * Partition metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartitionMetadata {
        private String topic;
        private Integer partition;
        private Integer leaderId;
        private List<Integer> followerIds;
        private List<Integer> isrIds;
        private Long leaderEpoch;
    }
}