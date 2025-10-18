package com.distributedmq.metadata.service;

import com.distributedmq.common.model.BrokerNode;
import com.distributedmq.common.model.BrokerStatus;
import com.distributedmq.common.model.PartitionMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of ControllerService
 * Handles cluster coordination and leader election
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControllerServiceImpl implements ControllerService {

    // In-memory broker registry (should be persisted in production)
    private final ConcurrentMap<Integer, BrokerNode> brokerRegistry = new ConcurrentHashMap<>();
    
    // Heartbeat tracking
    private final ConcurrentMap<Integer, Long> lastHeartbeat = new ConcurrentHashMap<>();

    private final MetadataPushService metadataPushService;

    @PostConstruct
    public void initializeDefaultBrokers() {
        // Add default brokers for development
        registerBroker(BrokerNode.builder()
                .brokerId(1)
                .host("localhost")
                .port(8081)
                .status(BrokerStatus.ONLINE)
                .build());
        registerBroker(BrokerNode.builder()
                .brokerId(2)
                .host("localhost")
                .port(8082)
                .status(BrokerStatus.ONLINE)
                .build());
        registerBroker(BrokerNode.builder()
                .brokerId(3)
                .host("localhost")
                .port(8083)
                .status(BrokerStatus.ONLINE)
                .build());
    }

    @Override
    public List<PartitionMetadata> assignPartitions(String topicName, int partitionCount, int replicationFactor) {
        log.info("Assigning {} partitions with replication factor {} for topic: {}", 
                partitionCount, replicationFactor, topicName);
        
        List<BrokerNode> activeBrokers = getActiveBrokers();
        
        if (activeBrokers.size() < replicationFactor) {
            throw new IllegalStateException(
                String.format("Not enough brokers for replication factor %d. Available: %d", 
                    replicationFactor, activeBrokers.size()));
        }
        
        List<PartitionMetadata> partitions = new ArrayList<>();
        
        for (int partitionId = 0; partitionId < partitionCount; partitionId++) {
            // Simple round-robin assignment
            int leaderIndex = partitionId % activeBrokers.size();
            BrokerNode leader = activeBrokers.get(leaderIndex);
            
            // Create replica list (leader + next brokers)
            List<BrokerNode> replicas = new ArrayList<>();
            List<BrokerNode> isr = new ArrayList<>();
            
            for (int i = 0; i < replicationFactor && i < activeBrokers.size(); i++) {
                int replicaIndex = (leaderIndex + i) % activeBrokers.size();
                BrokerNode replica = activeBrokers.get(replicaIndex);
                replicas.add(replica);
                isr.add(replica); // Initially all replicas are in ISR
            }
            
            PartitionMetadata partition = PartitionMetadata.builder()
                    .topicName(topicName)
                    .partitionId(partitionId)
                    .leader(leader)
                    .replicas(replicas)
                    .isr(isr)
                    .startOffset(0L)
                    .endOffset(0L)
                    .build();
            
            partitions.add(partition);
        }
        
        log.info("Successfully assigned {} partitions for topic: {}", partitions.size(), topicName);
        return partitions;
    }

    @Override
    public void cleanupTopicPartitions(String topicName) {
        log.info("Cleaning up partitions for topic: {}", topicName);
        
        // TODO: Notify storage nodes to delete partition data
        // This will be implemented when storage service metadata sync is added
        
        log.info("Partition cleanup completed for topic: {}", topicName);
    }

    @Override
    public void handleBrokerFailure(Integer brokerId) {
        log.warn("Handling failure for broker: {}", brokerId);
        
        BrokerNode broker = brokerRegistry.get(brokerId);
        if (broker != null) {
            broker.setStatus(BrokerStatus.OFFLINE);
            log.info("Marked broker {} as offline", brokerId);
        }
        
        // TODO: Re-elect leaders for partitions where this broker was leader
        // TODO: Update ISR lists
        // TODO: Trigger replication for under-replicated partitions

        // Push broker status change to storage nodes
        try {
            metadataPushService.pushFullClusterMetadata();
            log.info("Successfully pushed broker failure update for broker {}", brokerId);
        } catch (Exception e) {
            log.error("Failed to push broker failure update for broker {}: {}", brokerId, e.getMessage());
            // Don't fail failure handling if push fails
        }
    }

    @Override
    public BrokerNode electPartitionLeader(String topicName, int partition) {
        log.info("Electing leader for partition: {}-{}", topicName, partition);
        
        // TODO: Implement proper leader election logic
        // For now, return first available broker
        
        List<BrokerNode> activeBrokers = getActiveBrokers();
        if (!activeBrokers.isEmpty()) {
            BrokerNode newLeader = activeBrokers.get(0);
            
            // Push partition leadership change to storage nodes
            try {
                // Note: In a real implementation, we'd need to get the current leader first
                // For now, we'll assume leadership change and push with new leader
                // The followers and ISR would need to be determined from current partition metadata
                metadataPushService.pushPartitionLeadershipUpdate(
                    topicName, partition, newLeader.getBrokerId(), 
                    List.of(), List.of(newLeader.getBrokerId())); // Simplified for now
                log.info("Successfully pushed leadership change for partition {}-{} to broker {}", 
                    topicName, partition, newLeader.getBrokerId());
            } catch (Exception e) {
                log.error("Failed to push leadership change for partition {}-{}: {}", 
                    topicName, partition, e.getMessage());
                // Don't fail election if push fails
            }
            
            return newLeader;
        }
        
        throw new IllegalStateException("No active brokers available for leader election");
    }

    @Override
    public List<BrokerNode> getActiveBrokers() {
        return brokerRegistry.values().stream()
                .filter(broker -> broker.getStatus() == BrokerStatus.ONLINE)
                .collect(ArrayList::new, (list, broker) -> list.add(broker), ArrayList::addAll);
    }

    @Override
    public void registerBroker(BrokerNode broker) {
        log.info("Registering broker: {}", broker.getBrokerId());
        
        brokerRegistry.put(broker.getBrokerId(), broker);
        lastHeartbeat.put(broker.getBrokerId(), System.currentTimeMillis());
        
        log.info("Successfully registered broker: {}", broker.getBrokerId());

        // Push broker status change to storage nodes
        try {
            metadataPushService.pushFullClusterMetadata();
            log.info("Successfully pushed broker registration update for broker {}", broker.getBrokerId());
        } catch (Exception e) {
            log.error("Failed to push broker registration update for broker {}: {}", broker.getBrokerId(), e.getMessage());
            // Don't fail registration if push fails
        }
    }

    @Override
    public void unregisterBroker(Integer brokerId) {
        log.info("Unregistering broker: {}", brokerId);
        
        brokerRegistry.remove(brokerId);
        lastHeartbeat.remove(brokerId);
        
        // Handle as broker failure
        handleBrokerFailure(brokerId);
        
        log.info("Successfully unregistered broker: {}", brokerId);

        // Push broker status change to storage nodes
        try {
            metadataPushService.pushFullClusterMetadata();
            log.info("Successfully pushed broker unregistration update for broker {}", brokerId);
        } catch (Exception e) {
            log.error("Failed to push broker unregistration update for broker {}: {}", brokerId, e.getMessage());
            // Don't fail unregistration if push fails
        }
    }

    @Override
    public void updatePartitionLeadership(String topicName, int partitionId, Integer leaderId, List<Integer> followers, List<Integer> isr) {
        log.info("Updating partition leadership for {}-{}: leader={}, followers={}, isr={}",
                topicName, partitionId, leaderId, followers, isr);

        // This is a notification that partition leadership has changed
        // In a real implementation, this would update the controller's view of partition leadership
        // For now, we just log it and could trigger rebalancing if needed

        // TODO: Update internal partition leadership tracking
        // TODO: Validate the leadership change
        // TODO: Trigger ISR updates if needed
        // TODO: Notify other components about the change

        log.info("Partition leadership update processed for {}-{}", topicName, partitionId);
    }
}
