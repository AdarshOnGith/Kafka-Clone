package com.distributedmq.storage.replication;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Stores metadata about partitions, leaders, followers, and ISR
 * This data is updated by the metadata service
 */
@Slf4j
@Component
public class MetadataStore {

    // topic-partition -> leader broker ID
    private final Map<String, Integer> partitionLeaders = new ConcurrentHashMap<>();

    // topic-partition -> list of follower broker IDs
    private final Map<String, List<Integer>> partitionFollowers = new ConcurrentHashMap<>();

    // topic-partition -> list of ISR broker IDs
    private final Map<String, List<Integer>> partitionISR = new ConcurrentHashMap<>();

    // topic-partition -> leader epoch
    private final Map<String, Long> partitionLeaderEpochs = new ConcurrentHashMap<>();

    // broker ID -> broker info
    private final Map<Integer, BrokerInfo> brokers = new ConcurrentHashMap<>();

    // This broker's ID (injected from config)
    private Integer localBrokerId;

    public void setLocalBrokerId(Integer brokerId) {
        this.localBrokerId = brokerId;
    }

    /**
     * Update partition leadership information
     */
    public void updatePartitionLeadership(String topic, Integer partition,
                                        Integer leaderId, List<Integer> followers,
                                        List<Integer> isr, Long leaderEpoch) {
        String key = getPartitionKey(topic, partition);

        partitionLeaders.put(key, leaderId);
        partitionFollowers.put(key, new ArrayList<>(followers));
        partitionISR.put(key, new ArrayList<>(isr));
        partitionLeaderEpochs.put(key, leaderEpoch);

        log.info("Updated leadership for {}-{}: leader={}, followers={}, isr={}, epoch={}",
                topic, partition, leaderId, followers, isr, leaderEpoch);
    }

    /**
     * Check if this broker is the leader for a partition
     */
    public boolean isLeaderForPartition(String topic, Integer partition) {
        String key = getPartitionKey(topic, partition);
        Integer leaderId = partitionLeaders.get(key);
        return leaderId != null && leaderId.equals(localBrokerId);
    }

    /**
     * Check if this broker is a follower for a partition
     */
    public boolean isFollowerForPartition(String topic, Integer partition) {
        String key = getPartitionKey(topic, partition);
        List<Integer> followers = partitionFollowers.get(key);
        return followers != null && followers.contains(localBrokerId);
    }

    /**
     * Get followers for a partition
     */
    public List<BrokerInfo> getFollowersForPartition(String topic, Integer partition) {
        String key = getPartitionKey(topic, partition);
        List<Integer> followerIds = partitionFollowers.get(key);

        if (followerIds == null) {
            return Collections.emptyList();
        }

        return followerIds.stream()
                .map(brokers::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get ISR for a partition
     */
    public List<BrokerInfo> getISRForPartition(String topic, Integer partition) {
        String key = getPartitionKey(topic, partition);
        List<Integer> isrIds = partitionISR.get(key);

        if (isrIds == null) {
            return Collections.emptyList();
        }

        return isrIds.stream()
                .map(brokers::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get leader epoch for a partition
     */
    public Long getLeaderEpoch(String topic, Integer partition) {
        String key = getPartitionKey(topic, partition);
        return partitionLeaderEpochs.getOrDefault(key, 0L);
    }

    /**
     * Update broker information
     */
    public void updateBroker(BrokerInfo broker) {
        brokers.put(broker.getId(), broker);
        log.debug("Updated broker info: {}", broker);
    }

    /**
     * Remove broker (when it goes offline)
     */
    public void removeBroker(Integer brokerId) {
        brokers.remove(brokerId);
        log.info("Removed broker {}", brokerId);
    }

    /**
     * Get all known brokers
     */
    public List<BrokerInfo> getAllBrokers() {
        return new ArrayList<>(brokers.values());
    }

    /**
     * Get broker by ID
     */
    public BrokerInfo getBroker(Integer brokerId) {
        return brokers.get(brokerId);
    }

    private String getPartitionKey(String topic, Integer partition) {
        return topic + "-" + partition;
    }

    // TODO: Add methods to sync with metadata service
    // - fetchPartitionMetadata()
    // - registerBroker()
    // - heartbeat()
}