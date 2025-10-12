package com.distributedmq.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Metadata about a consumer group.
 * Tracks consumer group state and partition assignments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerGroupMetadata {
    
    /**
     * Consumer group ID.
     */
    private String groupId;
    
    /**
     * Group state (Stable, Rebalancing, Dead).
     */
    private GroupState state;
    
    /**
     * Protocol type (consumer, connect, etc.).
     */
    private String protocolType;
    
    /**
     * Current generation ID (incremented on each rebalance).
     */
    private int generationId;
    
    /**
     * Leader consumer ID.
     */
    private String leaderId;
    
    /**
     * Map of consumer ID to partition assignments.
     * Key: consumerId, Value: List of assigned partitions
     */
    private Map<String, java.util.List<TopicPartition>> assignments;
    
    /**
     * List of all members in the group.
     */
    private java.util.List<ConsumerMember> members;
    
    /**
     * Represents a consumer group member.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsumerMember {
        private String memberId;
        private String clientId;
        private String clientHost;
        private long sessionTimeoutMs;
        private long rebalanceTimeoutMs;
    }
    
    /**
     * Represents a topic-partition pair.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicPartition {
        private String topic;
        private int partition;
    }
    
    /**
     * Consumer group states.
     */
    public enum GroupState {
        EMPTY,           // No members
        PREPARING_REBALANCE,  // Waiting for all members to rejoin
        COMPLETING_REBALANCE, // Waiting for assignment completion
        STABLE,          // Normal operation
        DEAD             // Group has been removed
    }
}
