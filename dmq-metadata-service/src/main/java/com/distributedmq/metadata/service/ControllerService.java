package com.distributedmq.metadata.service;

import com.distributedmq.common.model.BrokerNode;
import com.distributedmq.common.model.PartitionMetadata;

import java.util.List;

/**
 * Controller Service for cluster coordination
 * Handles failure detection, leader election, and replication
 */
public interface ControllerService {

    /**
     * Assign partitions to brokers
     */
    List<PartitionMetadata> assignPartitions(String topicName, int partitionCount, int replicationFactor);

    /**
     * Cleanup topic partitions on deletion
     */
    void cleanupTopicPartitions(String topicName);

    /**
     * Handle broker failure
     */
    void handleBrokerFailure(Integer brokerId);

    /**
     * Elect new leader for partition
     */
    BrokerNode electPartitionLeader(String topicName, int partition);

    /**
     * Get all active brokers
     */
    List<BrokerNode> getActiveBrokers();

    /**
     * Register a new broker
     */
    void registerBroker(BrokerNode broker);

    /**
     * Unregister a broker
     */
    void unregisterBroker(Integer brokerId);

    /**
     * Update partition leadership information
     */
    void updatePartitionLeadership(String topicName, int partitionId, Integer leaderId, List<Integer> followers, List<Integer> isr);

    // TODO: Add ISR management
    // TODO: Add rebalancing logic
}
