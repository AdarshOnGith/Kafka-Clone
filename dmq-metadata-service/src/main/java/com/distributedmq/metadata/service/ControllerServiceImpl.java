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

    // Partition metadata storage (topic -> partitionId -> PartitionMetadata)
    private final ConcurrentMap<String, ConcurrentMap<Integer, PartitionMetadata>> partitionRegistry = new ConcurrentHashMap<>();

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

        // Get available storage nodes instead of all brokers
        List<com.distributedmq.common.config.ServiceDiscovery.StorageServiceInfo> storageServices =
            com.distributedmq.common.config.ServiceDiscovery.getAllStorageServices();

        if (storageServices.isEmpty()) {
            throw new IllegalStateException("No storage services available for partition assignment");
        }

        if (storageServices.size() < replicationFactor) {
            throw new IllegalStateException(
                String.format("Not enough storage services for replication factor %d. Available: %d",
                    replicationFactor, storageServices.size()));
        }

        List<PartitionMetadata> partitions = new ArrayList<>();

        for (int partitionId = 0; partitionId < partitionCount; partitionId++) {
            // Round-robin assignment across available storage nodes
            int startIndex = partitionId % storageServices.size();

            // Select replicas based on replication factor
            List<BrokerNode> replicas = new ArrayList<>();
            for (int i = 0; i < replicationFactor; i++) {
                int storageIndex = (startIndex + i) % storageServices.size();
                com.distributedmq.common.config.ServiceDiscovery.StorageServiceInfo storageService =
                    storageServices.get(storageIndex);

                // Create broker node from storage service info
                BrokerNode brokerNode = BrokerNode.builder()
                        .brokerId(storageService.getId())
                        .host(storageService.getHost())
                        .port(storageService.getPort())
                        .status(BrokerStatus.ONLINE)
                        .build();

                replicas.add(brokerNode);
            }

            // Assign first replica as leader
            BrokerNode leader = replicas.get(0);

            // Initially all replicas are in ISR
            List<BrokerNode> isr = new ArrayList<>(replicas);

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

            // Store in partition registry
            partitionRegistry.computeIfAbsent(topicName, k -> new ConcurrentHashMap<>())
                    .put(partitionId, partition);
        }

        log.info("Successfully assigned {} partitions for topic: {} using {} storage services",
                partitions.size(), topicName, storageServices.size());
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
            metadataPushService.pushFullClusterMetadata(getActiveBrokers());
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
            metadataPushService.pushFullClusterMetadata(getActiveBrokers());
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
            metadataPushService.pushFullClusterMetadata(getActiveBrokers());
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

        // Get or create partition metadata
        ConcurrentMap<Integer, PartitionMetadata> topicPartitions = partitionRegistry.computeIfAbsent(topicName, k -> new ConcurrentHashMap<>());
        PartitionMetadata partition = topicPartitions.get(partitionId);

        if (partition == null) {
            // Create new partition metadata if it doesn't exist
            partition = PartitionMetadata.builder()
                    .topicName(topicName)
                    .partitionId(partitionId)
                    .startOffset(0L)
                    .endOffset(0L)
                    .build();
        }

        // Update leader
        if (leaderId != null) {
            BrokerNode leader = brokerRegistry.get(leaderId);
            if (leader != null) {
                partition.setLeader(leader);
            }
        }

        // Update replicas and ISR from the provided lists
        if (followers != null && isr != null) {
            List<BrokerNode> replicas = new ArrayList<>();
            List<BrokerNode> isrNodes = new ArrayList<>();

            // Add leader to replicas if not already included
            if (leaderId != null && !followers.contains(leaderId)) {
                followers = new ArrayList<>(followers);
                followers.add(0, leaderId); // Leader should be first
            }

            // Convert broker IDs to BrokerNode objects
            for (Integer brokerId : followers) {
                BrokerNode broker = brokerRegistry.get(brokerId);
                if (broker != null) {
                    replicas.add(broker);
                }
            }

            for (Integer brokerId : isr) {
                BrokerNode broker = brokerRegistry.get(brokerId);
                if (broker != null) {
                    isrNodes.add(broker);
                }
            }

            partition.setReplicas(replicas);
            partition.setIsr(isrNodes);
        }

        // Store updated partition
        topicPartitions.put(partitionId, partition);

        log.info("Partition leadership update processed for {}-{}", topicName, partitionId);
    }

    @Override
    public void removeFromISR(String topicName, int partitionId, Integer brokerId) {
        log.info("Removing broker {} from ISR for partition {}-{}", brokerId, topicName, partitionId);

        ConcurrentMap<Integer, PartitionMetadata> topicPartitions = partitionRegistry.get(topicName);
        if (topicPartitions == null) {
            log.warn("Topic {} not found when removing from ISR", topicName);
            return;
        }

        PartitionMetadata partition = topicPartitions.get(partitionId);
        if (partition == null) {
            log.warn("Partition {}-{} not found when removing from ISR", topicName, partitionId);
            return;
        }

        List<BrokerNode> currentISR = partition.getIsr();
        if (currentISR != null) {
            boolean removed = currentISR.removeIf(broker -> broker.getBrokerId().equals(brokerId));
            if (removed) {
                log.info("Successfully removed broker {} from ISR for {}-{}", brokerId, topicName, partitionId);
            } else {
                log.debug("Broker {} was not in ISR for {}-{}", brokerId, topicName, partitionId);
            }
        }
    }

    @Override
    public void addToISR(String topicName, int partitionId, Integer brokerId) {
        log.info("Adding broker {} to ISR for partition {}-{}", brokerId, topicName, partitionId);

        ConcurrentMap<Integer, PartitionMetadata> topicPartitions = partitionRegistry.get(topicName);
        if (topicPartitions == null) {
            log.warn("Topic {} not found when adding to ISR", topicName);
            return;
        }

        PartitionMetadata partition = topicPartitions.get(partitionId);
        if (partition == null) {
            log.warn("Partition {}-{} not found when adding to ISR", topicName, partitionId);
            return;
        }

        // Check if broker is in replicas
        boolean isReplica = partition.getReplicas().stream()
                .anyMatch(broker -> broker.getBrokerId().equals(brokerId));

        if (!isReplica) {
            log.warn("Broker {} is not a replica for {}-{}, cannot add to ISR", brokerId, topicName, partitionId);
            return;
        }

        List<BrokerNode> currentISR = partition.getIsr();
        if (currentISR == null) {
            currentISR = new ArrayList<>();
            partition.setIsr(currentISR);
        }

        boolean alreadyInISR = currentISR.stream()
                .anyMatch(broker -> broker.getBrokerId().equals(brokerId));

        if (!alreadyInISR) {
            BrokerNode brokerNode = brokerRegistry.get(brokerId);
            if (brokerNode != null) {
                currentISR.add(brokerNode);
                log.info("Successfully added broker {} to ISR for {}-{}", brokerId, topicName, partitionId);
            } else {
                log.warn("Broker {} not found in broker registry", brokerId);
            }
        } else {
            log.debug("Broker {} is already in ISR for {}-{}", brokerId, topicName, partitionId);
        }
    }

    @Override
    public List<Integer> getISR(String topicName, int partitionId) {
        ConcurrentMap<Integer, PartitionMetadata> topicPartitions = partitionRegistry.get(topicName);
        if (topicPartitions == null) {
            return new ArrayList<>();
        }

        PartitionMetadata partition = topicPartitions.get(partitionId);
        if (partition == null || partition.getIsr() == null) {
            return new ArrayList<>();
        }

        return partition.getIsr().stream()
                .map(BrokerNode::getBrokerId)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public Integer getPartitionLeader(String topicName, int partitionId) {
        ConcurrentMap<Integer, PartitionMetadata> topicPartitions = partitionRegistry.get(topicName);
        if (topicPartitions == null) {
            return null;
        }

        PartitionMetadata partition = topicPartitions.get(partitionId);
        if (partition == null || partition.getLeader() == null) {
            return null;
        }

        return partition.getLeader().getBrokerId();
    }

    @Override
    public List<Integer> getPartitionFollowers(String topicName, int partitionId) {
        ConcurrentMap<Integer, PartitionMetadata> topicPartitions = partitionRegistry.get(topicName);
        if (topicPartitions == null) {
            return new ArrayList<>();
        }

        PartitionMetadata partition = topicPartitions.get(partitionId);
        if (partition == null || partition.getReplicas() == null) {
            return new ArrayList<>();
        }

        Integer leaderId = getPartitionLeader(topicName, partitionId);
        return partition.getReplicas().stream()
                .map(BrokerNode::getBrokerId)
                .filter(id -> !id.equals(leaderId))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
