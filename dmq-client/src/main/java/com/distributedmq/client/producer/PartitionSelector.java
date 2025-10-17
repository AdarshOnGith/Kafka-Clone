package com.distributedmq.client.producer;

import com.distributedmq.common.model.PartitionMetadata;
import com.distributedmq.common.model.TopicMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles partition selection logic for message routing
 * Uses murmur hash for keyed messages, sticky partitioning for non-keyed messages
 */
@Slf4j
public class PartitionSelector {

    private final AtomicInteger stickyPartitionIndex = new AtomicInteger(0);

    /**
     * Select partition for a message based on key
     *
     * @param metadata Topic metadata containing partition information
     * @param key Message key (can be null for sticky partitioning)
     * @return Selected partition metadata
     */
    public PartitionMetadata selectPartition(TopicMetadata metadata, String key) {
        List<PartitionMetadata> partitions = metadata.getPartitions();

        if (partitions == null || partitions.isEmpty()) {
            throw new IllegalStateException("No partitions available for topic: " + metadata.getTopicName());
        }

        int partitionIndex;

        if (key != null && !key.trim().isEmpty()) {
            // Murmur hash-based partitioning for keyed messages
            partitionIndex = Math.abs(murmurHash(key) % partitions.size());
            log.debug("Selected partition {} for key '{}' using murmur hash partitioning", partitionIndex, key);
        } else {
            // Sticky partitioning for non-keyed messages (same partition until conditions change)
            partitionIndex = stickyPartitionIndex.get() % partitions.size();
            log.debug("Selected partition {} using sticky partitioning", partitionIndex);
        }

        PartitionMetadata selectedPartition = partitions.get(partitionIndex);
        log.info("🎯 Selected partition {} (leader: {})", selectedPartition.getPartitionId(),
                selectedPartition.getLeader().getAddress());

        return selectedPartition;
    }

    /**
     * Get the leader broker for a selected partition
     *
     * @param partition Selected partition metadata
     * @return Leader broker information
     */
    public com.distributedmq.common.model.BrokerNode getLeaderBroker(PartitionMetadata partition) {
        com.distributedmq.common.model.BrokerNode leader = partition.getLeader();
        if (leader == null) {
            throw new IllegalStateException("No leader found for partition: " + partition.getPartitionId());
        }

        log.debug("Leader broker for partition {}: {} (status: {})",
                partition.getPartitionId(), leader.getAddress(), leader.getStatus());

        return leader;
    }

    /**
     * MurmurHash 2.0 implementation for consistent hashing
     */
    private int murmurHash(String key) {
        final int seed = 0x9747b28c; // Standard murmur seed
        final int m = 0x5bd1e995;
        final int r = 24;

        byte[] data = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int length = data.length;

        int h = seed ^ length;

        int i = 0;
        while (length >= 4) {
            int k = (data[i] & 0xFF) |
                   ((data[i + 1] & 0xFF) << 8) |
                   ((data[i + 2] & 0xFF) << 16) |
                   ((data[i + 3] & 0xFF) << 24);

            k *= m;
            k ^= k >>> r;
            k *= m;

            h *= m;
            h ^= k;

            i += 4;
            length -= 4;
        }

        // Handle remaining bytes
        switch (length) {
            case 3: h ^= (data[i + 2] & 0xFF) << 16;
            case 2: h ^= (data[i + 1] & 0xFF) << 8;
            case 1: h ^= (data[i] & 0xFF);
                    h *= m;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        return h;
    }
}