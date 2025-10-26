package com.distributedmq.metadata.service;

import com.distributedmq.common.config.ServiceDiscovery;
import com.distributedmq.common.dto.MetadataUpdateRequest;
import com.distributedmq.common.dto.MetadataUpdateResponse;
import com.distributedmq.common.model.BrokerNode;
import com.distributedmq.common.model.PartitionMetadata;
import com.distributedmq.common.model.TopicMetadata;
import com.distributedmq.metadata.config.ServicePairingConfig;
import com.distributedmq.metadata.coordination.MetadataStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for pushing metadata updates to storage nodes
 * Handles the push mechanism of metadata synchronization
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataPushService {

    private final StorageNodeClient storageNodeClient;
    private final MetadataStateMachine metadataStateMachine;

    /**
     * Push topic metadata to all storage nodes
     * Called after topic creation or updates
     */
    public List<MetadataUpdateResponse> pushTopicMetadata(TopicMetadata topicMetadata, List<BrokerNode> activeBrokers) {
        log.info("Pushing topic metadata for topic: {} to all storage nodes",
                topicMetadata.getTopicName());

        // Convert TopicMetadata to MetadataUpdateRequest
        MetadataUpdateRequest request = createMetadataUpdateRequest(topicMetadata, activeBrokers);

        // Get all storage service URLs
        List<String> storageUrls = ServiceDiscovery.getAllStorageServices().stream()
                .map(ServiceDiscovery.StorageServiceInfo::getUrl)
                .collect(Collectors.toList());

        log.info("Pushing to {} storage nodes: {}", storageUrls.size(), storageUrls);

        // Push to all storage nodes
        List<MetadataUpdateResponse> responses = storageUrls.stream()
                .map(url -> pushToStorageNode(url, request))
                .collect(Collectors.toList());

        // Log results
        long successCount = responses.stream().filter(MetadataUpdateResponse::isSuccess).count();
        log.info("Topic metadata push completed: {}/{} storage nodes successful",
                successCount, responses.size());

        return responses;
    }

    /**
     * Push full cluster metadata to all storage nodes
     * Used for initial sync or major updates
     */
    public List<MetadataUpdateResponse> pushFullClusterMetadata(List<BrokerNode> activeBrokers) {
        log.info("Pushing full cluster metadata to all storage nodes");

        // Create metadata update request with all brokers
        MetadataUpdateRequest request = MetadataUpdateRequest.builder()
                .version(metadataStateMachine.getMetadataVersion())
                .brokers(activeBrokers.stream()
                        .map(this::convertBrokerNodeToBrokerInfo)
                        .collect(Collectors.toList()))
                .timestamp(System.currentTimeMillis())
                .build();

        // Get all storage service URLs
        List<String> storageUrls = ServiceDiscovery.getAllStorageServices().stream()
                .map(ServiceDiscovery.StorageServiceInfo::getUrl)
                .collect(Collectors.toList());

        log.info("Pushing cluster metadata to {} storage nodes: {}", storageUrls.size(), storageUrls);

        // Push to all storage nodes
        List<MetadataUpdateResponse> responses = storageUrls.stream()
                .map(url -> pushToStorageNode(url, request))
                .collect(Collectors.toList());

        // Log results
        long successCount = responses.stream().filter(MetadataUpdateResponse::isSuccess).count();
        log.info("Cluster metadata push completed: {}/{} storage nodes successful",
                successCount, responses.size());

        return responses;
    }

    /**
     * Push metadata update to a specific storage node URL
     * Used by controller for cross-node coordination
     */
    public MetadataUpdateResponse pushToStorageNode(String storageNodeUrl, MetadataUpdateRequest request) {
        log.debug("Pushing metadata update to storage node: {}", storageNodeUrl);

        return storageNodeClient.pushMetadataToUrl(storageNodeUrl, request);
    }

    /**
     * Push partition leadership changes to all storage nodes
     * Called when partition leaders change
     */
    public List<MetadataUpdateResponse> pushPartitionLeadershipUpdate(String topicName, int partitionId,
                                            int newLeaderId, List<Integer> followers,
                                            List<Integer> isr) {
        log.info("Pushing partition leadership update for {}-{}: leader={} to all storage nodes",
                topicName, partitionId, newLeaderId);

        // Create partition metadata update
        MetadataUpdateRequest.PartitionMetadata partitionMetadata =
                MetadataUpdateRequest.PartitionMetadata.builder()
                        .topic(topicName)
                        .partition(partitionId)
                        .leaderId(newLeaderId)
                        .followerIds(followers)
                        .isrIds(isr)
                        .leaderEpoch(System.currentTimeMillis()) // Use timestamp as epoch
                        .build();

        MetadataUpdateRequest request = MetadataUpdateRequest.builder()
                .version(metadataStateMachine.getMetadataVersion())
                .partitions(List.of(partitionMetadata))
                .timestamp(System.currentTimeMillis())
                .build();

        // Get all storage service URLs
        List<String> storageUrls = ServiceDiscovery.getAllStorageServices().stream()
                .map(ServiceDiscovery.StorageServiceInfo::getUrl)
                .collect(Collectors.toList());

        // Push to all storage nodes
        List<MetadataUpdateResponse> responses = storageUrls.stream()
                .map(url -> pushToStorageNode(url, request))
                .collect(Collectors.toList());

        // Log results
        long successCount = responses.stream().filter(MetadataUpdateResponse::isSuccess).count();
        log.info("Partition leadership update push completed: {}/{} storage nodes successful",
                successCount, responses.size());

        return responses;
    }

    /**
     * Push topic deletion update to all storage nodes
     * Called after topic deletion to notify brokers to remove partition metadata
     */
    public List<MetadataUpdateResponse> pushTopicDeletionUpdate(String topicName) {
        log.info("Pushing topic deletion update for topic: {} to all storage nodes", topicName);

        // Create metadata update request with deletion flag
        // We send an update with the topic name but empty partitions to signal deletion
        MetadataUpdateRequest request = MetadataUpdateRequest.builder()
                .version(metadataStateMachine.getMetadataVersion())
                .deletedTopics(List.of(topicName))  // Use deletedTopics field if available
                .timestamp(System.currentTimeMillis())
                .build();

        // Get all storage service URLs
        List<String> storageUrls = ServiceDiscovery.getAllStorageServices().stream()
                .map(ServiceDiscovery.StorageServiceInfo::getUrl)
                .collect(Collectors.toList());

        log.info("Pushing topic deletion to {} storage nodes: {}", storageUrls.size(), storageUrls);

        // Push to all storage nodes
        List<MetadataUpdateResponse> responses = storageUrls.stream()
                .map(url -> pushToStorageNode(url, request))
                .collect(Collectors.toList());

        // Log results
        long successCount = responses.stream().filter(MetadataUpdateResponse::isSuccess).count();
        log.info("Topic deletion push completed: {}/{} storage nodes successful",
                successCount, responses.size());

        return responses;
    }

    /**
     * Convert TopicMetadata to MetadataUpdateRequest
     */
    private MetadataUpdateRequest createMetadataUpdateRequest(TopicMetadata topicMetadata, List<BrokerNode> activeBrokers) {
        // Convert partitions
        List<MetadataUpdateRequest.PartitionMetadata> partitionMetadatas =
                topicMetadata.getPartitions().stream()
                        .map(this::convertPartitionMetadata)
                        .collect(Collectors.toList());

        // Use provided active brokers for broker info
        List<MetadataUpdateRequest.BrokerInfo> brokerInfos = activeBrokers.stream()
                .map(this::convertBrokerNodeToBrokerInfo)
                .collect(Collectors.toList());

        return MetadataUpdateRequest.builder()
                .version(metadataStateMachine.getMetadataVersion())
                .brokers(brokerInfos)
                .partitions(partitionMetadatas)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Convert PartitionMetadata to MetadataUpdateRequest.PartitionMetadata
     */
    private MetadataUpdateRequest.PartitionMetadata convertPartitionMetadata(
            PartitionMetadata partitionMetadata) {

        return MetadataUpdateRequest.PartitionMetadata.builder()
                .topic(partitionMetadata.getTopicName())
                .partition(partitionMetadata.getPartitionId())
                .leaderId(partitionMetadata.getLeader().getBrokerId())
                .followerIds(partitionMetadata.getReplicas().stream()
                        .map(broker -> broker.getBrokerId())
                        .filter(id -> !id.equals(partitionMetadata.getLeader().getBrokerId()))
                        .collect(Collectors.toList()))
                .isrIds(partitionMetadata.getIsr().stream()
                        .map(broker -> broker.getBrokerId())
                        .collect(Collectors.toList()))
                .leaderEpoch(System.currentTimeMillis())
                .build();
    }

    /**
     * Convert BrokerNode to MetadataUpdateRequest.BrokerInfo
     */
    private MetadataUpdateRequest.BrokerInfo convertBrokerNodeToBrokerInfo(BrokerNode brokerNode) {
        return MetadataUpdateRequest.BrokerInfo.builder()
                .id(brokerNode.getBrokerId())
                .host(brokerNode.getHost())
                .port(brokerNode.getPort())
                .isAlive(brokerNode.getStatus() == com.distributedmq.common.model.BrokerStatus.ONLINE)
                .build();
    }
}