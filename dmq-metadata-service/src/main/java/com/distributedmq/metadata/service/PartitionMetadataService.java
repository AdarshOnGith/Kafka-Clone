package com.distributedmq.metadata.service;

import com.distributedmq.common.exception.ErrorCode;
import com.distributedmq.common.exception.MetadataException;
import com.distributedmq.common.model.PartitionMetadata;
import com.distributedmq.metadata.entity.PartitionEntity;
import com.distributedmq.metadata.entity.PartitionReplicaEntity;
import com.distributedmq.metadata.entity.TopicEntity;
import com.distributedmq.metadata.repository.PartitionReplicaRepository;
import com.distributedmq.metadata.repository.PartitionRepository;
import com.distributedmq.metadata.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing partition metadata.
 * Implements Flow 1 Step 4: Leader Discovery
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartitionMetadataService {
    
    private final TopicRepository topicRepository;
    private final PartitionRepository partitionRepository;
    private final PartitionReplicaRepository replicaRepository;
    
    /**
     * Get partition metadata (cached for 60 seconds).
     * Returns leader information for Producer Ingestion Service.
     */
    @Cacheable(value = "partitionMetadata", key = "#topicName + ':' + #partitionNumber")
    @Transactional(readOnly = true)
    public PartitionMetadata getPartitionMetadata(String topicName, int partitionNumber) {
        log.debug("Fetching partition metadata for topic={}, partition={}", topicName, partitionNumber);
        
        TopicEntity topic = topicRepository.findByTopicName(topicName)
                .orElseThrow(() -> new MetadataException(
                        ErrorCode.TOPIC_NOT_FOUND,
                        "Topic not found: " + topicName
                ));
        
        PartitionEntity partition = partitionRepository
                .findByTopicIdAndPartitionNumber(topic.getTopicId(), partitionNumber)
                .orElseThrow(() -> new MetadataException(
                        ErrorCode.PARTITION_NOT_FOUND,
                        String.format("Partition %d not found for topic %s", partitionNumber, topicName)
                ));
        
        if (partition.getLeaderNodeId() == null) {
            throw new MetadataException(
                    ErrorCode.LEADER_NOT_AVAILABLE,
                    String.format("No leader available for partition %s-%d", topicName, partitionNumber)
            );
        }
        
        // Get ISR (In-Sync Replicas)
        List<String> inSyncReplicas = replicaRepository
                .findInSyncReplicasByPartitionId(partition.getPartitionId())
                .stream()
                .map(PartitionReplicaEntity::getNodeId)
                .collect(Collectors.toList());
        
        return PartitionMetadata.builder()
                .topicName(topicName)
                .partitionNumber(partitionNumber)
                .leaderNodeId(partition.getLeaderNodeId())
                .leaderNodeAddress(partition.getLeaderNodeAddress())
                .leaderEpoch(partition.getLeaderEpoch())
                .highWaterMark(partition.getHighWaterMark())
                .inSyncReplicas(inSyncReplicas)
                .build();
    }
    
    /**
     * Get all partitions for a topic.
     */
    @Cacheable(value = "topicMetadata", key = "#topicName")
    @Transactional(readOnly = true)
    public List<PartitionMetadata> getTopicMetadata(String topicName) {
        log.debug("Fetching all partitions for topic={}", topicName);
        
        TopicEntity topic = topicRepository.findByTopicName(topicName)
                .orElseThrow(() -> new MetadataException(
                        ErrorCode.TOPIC_NOT_FOUND,
                        "Topic not found: " + topicName
                ));
        
        List<PartitionEntity> partitions = partitionRepository.findByTopicId(topic.getTopicId());
        
        return partitions.stream()
                .map(partition -> {
                    List<String> isr = replicaRepository
                            .findInSyncReplicasByPartitionId(partition.getPartitionId())
                            .stream()
                            .map(PartitionReplicaEntity::getNodeId)
                            .collect(Collectors.toList());
                    
                    return PartitionMetadata.builder()
                            .topicName(topicName)
                            .partitionNumber(partition.getPartitionNumber())
                            .leaderNodeId(partition.getLeaderNodeId())
                            .leaderNodeAddress(partition.getLeaderNodeAddress())
                            .leaderEpoch(partition.getLeaderEpoch())
                            .highWaterMark(partition.getHighWaterMark())
                            .inSyncReplicas(isr)
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Update partition leader (called by Controller Service).
     * Implements Flow 3: Controller updates leader after failure detection.
     */
    @CacheEvict(value = {"partitionMetadata", "topicMetadata"}, allEntries = true)
    @Transactional
    public void updatePartitionLeader(String topicName, int partitionNumber, 
                                     String newLeaderNodeId, String newLeaderAddress, int newEpoch) {
        log.info("Updating partition leader: topic={}, partition={}, newLeader={}, epoch={}", 
                topicName, partitionNumber, newLeaderNodeId, newEpoch);
        
        TopicEntity topic = topicRepository.findByTopicName(topicName)
                .orElseThrow(() -> new MetadataException(ErrorCode.TOPIC_NOT_FOUND));
        
        PartitionEntity partition = partitionRepository
                .findByTopicIdAndPartitionNumber(topic.getTopicId(), partitionNumber)
                .orElseThrow(() -> new MetadataException(ErrorCode.PARTITION_NOT_FOUND));
        
        partition.setLeaderNodeId(newLeaderNodeId);
        partition.setLeaderNodeAddress(newLeaderAddress);
        partition.setLeaderEpoch(newEpoch);
        
        partitionRepository.save(partition);
        
        log.info("Partition leader updated successfully");
    }
    
    /**
     * Update ISR (In-Sync Replicas) for a partition.
     */
    @CacheEvict(value = {"partitionMetadata", "topicMetadata"}, allEntries = true)
    @Transactional
    public void updateInSyncReplicas(String topicName, int partitionNumber, List<String> isrNodeIds) {
        log.debug("Updating ISR for topic={}, partition={}, isr={}", topicName, partitionNumber, isrNodeIds);
        
        TopicEntity topic = topicRepository.findByTopicName(topicName)
                .orElseThrow(() -> new MetadataException(ErrorCode.TOPIC_NOT_FOUND));
        
        PartitionEntity partition = partitionRepository
                .findByTopicIdAndPartitionNumber(topic.getTopicId(), partitionNumber)
                .orElseThrow(() -> new MetadataException(ErrorCode.PARTITION_NOT_FOUND));
        
        List<PartitionReplicaEntity> replicas = replicaRepository.findByPartitionId(partition.getPartitionId());
        
        for (PartitionReplicaEntity replica : replicas) {
            replica.setIsInSync(isrNodeIds.contains(replica.getNodeId()));
        }
        
        replicaRepository.saveAll(replicas);
    }
}
