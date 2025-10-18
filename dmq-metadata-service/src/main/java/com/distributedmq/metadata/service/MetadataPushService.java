package com.distributedmq.metadata.service;

import com.distributedmq.common.dto.MetadataUpdateRequest;
import com.distributedmq.common.dto.MetadataUpdateResponse;
import com.distributedmq.common.model.BrokerNode;
import com.distributedmq.common.model.PartitionMetadata;
import com.distributedmq.common.model.TopicMetadata;
import com.distributedmq.metadata.config.ServicePairingConfig;
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
    private final ControllerService controllerService;

    /**
     * Push topic metadata to the paired storage node
     * Called after topic creation or updates
     */
    public MetadataUpdateResponse pushTopicMetadata(TopicMetadata topicMetadata) {
        log.info("Pushing topic metadata for topic: {} to paired storage node",
                topicMetadata.getTopicName());

        // Convert TopicMetadata to MetadataUpdateRequest
        MetadataUpdateRequest request = createMetadataUpdateRequest(topicMetadata);

        // Push to paired storage node
        MetadataUpdateResponse response = storageNodeClient.pushMetadata(request);

        if (response.isSuccess()) {
            log.info("Successfully pushed topic metadata for {} to storage node {}",
                    topicMetadata.getTopicName(), storageNodeClient.getPairedStorageNodeBrokerId());
        } else {
            log.error("Failed to push topic metadata for {} to storage node {}. Error: {}",
                    topicMetadata.getTopicName(),
                    storageNodeClient.getPairedStorageNodeBrokerId(),
                    response.getErrorMessage());
        }

        return response;
    }

    /**
     * Push full cluster metadata to the paired storage node
     * Used for initial sync or major updates
     */
    public MetadataUpdateResponse pushFullClusterMetadata() {
        log.info("Pushing full cluster metadata to paired storage node");

        // Get all active brokers
        List<BrokerNode> activeBrokers = controllerService.getActiveBrokers();

        // Create metadata update request with all brokers
        MetadataUpdateRequest request = MetadataUpdateRequest.builder()
                .brokers(activeBrokers.stream()
                        .map(this::convertBrokerNodeToBrokerInfo)
                        .collect(Collectors.toList()))
                .timestamp(System.currentTimeMillis())
                .build();

        // Push to paired storage node
        MetadataUpdateResponse response = storageNodeClient.pushMetadata(request);

        if (response.isSuccess()) {
            log.info("Successfully pushed full cluster metadata to storage node {}",
                    storageNodeClient.getPairedStorageNodeBrokerId());
        } else {
            log.error("Failed to push full cluster metadata to storage node {}. Error: {}",
                    storageNodeClient.getPairedStorageNodeBrokerId(),
                    response.getErrorMessage());
        }

        return response;
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
     * Push partition leadership changes to storage nodes
     * Called when partition leaders change
     */
    public void pushPartitionLeadershipUpdate(String topicName, int partitionId,
                                            int newLeaderId, List<Integer> followers,
                                            List<Integer> isr) {
        log.info("Pushing partition leadership update for {}-{}: leader={}",
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
                .partitions(List.of(partitionMetadata))
                .timestamp(System.currentTimeMillis())
                .build();

        // Push to paired storage node
        MetadataUpdateResponse response = storageNodeClient.pushMetadata(request);

        if (response.isSuccess()) {
            log.info("Successfully pushed partition leadership update for {}-{}",
                    topicName, partitionId);
        } else {
            log.error("Failed to push partition leadership update for {}-{}. Error: {}",
                    topicName, partitionId, response.getErrorMessage());
        }
    }

    /**
     * Convert TopicMetadata to MetadataUpdateRequest
     */
    private MetadataUpdateRequest createMetadataUpdateRequest(TopicMetadata topicMetadata) {
        // Convert partitions
        List<MetadataUpdateRequest.PartitionMetadata> partitionMetadatas =
                topicMetadata.getPartitions().stream()
                        .map(this::convertPartitionMetadata)
                        .collect(Collectors.toList());

        // Get all active brokers for broker info
        List<BrokerNode> activeBrokers = controllerService.getActiveBrokers();
        List<MetadataUpdateRequest.BrokerInfo> brokerInfos = activeBrokers.stream()
                .map(this::convertBrokerNodeToBrokerInfo)
                .collect(Collectors.toList());

        return MetadataUpdateRequest.builder()
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
                .lastHeartbeat(System.currentTimeMillis())
                .build();
    }
}