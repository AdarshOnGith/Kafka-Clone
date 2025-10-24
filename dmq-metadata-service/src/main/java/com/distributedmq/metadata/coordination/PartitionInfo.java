package com.distributedmq.metadata.coordination;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Information about a partition
 */
@Data
@Builder
public class PartitionInfo {
    private final int partitionId;
    private final int leader;
    private final List<Integer> replicas;
    private final List<Integer> isr;
}