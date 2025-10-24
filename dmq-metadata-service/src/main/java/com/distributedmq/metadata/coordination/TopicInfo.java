package com.distributedmq.metadata.coordination;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Information about a topic
 */
@Data
@Builder
public class TopicInfo {
    private final String name;
    private final int numPartitions;
    private final short replicationFactor;
    private final Map<Integer, PartitionInfo> partitions;
    private final long createdTime;
}