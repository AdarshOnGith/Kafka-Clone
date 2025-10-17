package com.distributedmq.metadata.service;

import com.distributedmq.common.model.TopicConfig;
import com.distributedmq.common.model.TopicMetadata;
import com.distributedmq.metadata.dto.CreateTopicRequest;
import com.distributedmq.metadata.entity.TopicEntity;
import com.distributedmq.metadata.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of MetadataService
 * Business logic layer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataServiceImpl implements MetadataService {

    private final TopicRepository topicRepository;
    private final ControllerService controllerService;

    @Override
    @Transactional
    public TopicMetadata createTopic(CreateTopicRequest request) {
        log.info("Creating topic: {} with {} partitions, replication factor: {}",
                request.getTopicName(), request.getPartitionCount(), request.getReplicationFactor());

        // Check if topic already exists
        Optional<TopicEntity> existingTopic = topicRepository.findByTopicName(request.getTopicName());
        if (existingTopic.isPresent()) {
            throw new IllegalArgumentException("Topic already exists: " + request.getTopicName());
        }

        // Create topic configuration with defaults
        TopicConfig config = TopicConfig.builder()
                .retentionMs(request.getRetentionMs() != null ? request.getRetentionMs() : 604800000L) // 7 days
                .retentionBytes(request.getRetentionBytes() != null ? request.getRetentionBytes() : -1L) // unlimited
                .segmentBytes(request.getSegmentBytes() != null ? request.getSegmentBytes() : 1073741824) // 1GB
                .compressionType(request.getCompressionType() != null ? request.getCompressionType() : "none")
                .minInsyncReplicas(request.getMinInsyncReplicas() != null ? request.getMinInsyncReplicas() : 1)
                .build();

        // Create topic metadata
        TopicMetadata metadata = TopicMetadata.builder()
                .topicName(request.getTopicName())
                .partitionCount(request.getPartitionCount())
                .replicationFactor(request.getReplicationFactor())
                .createdAt(System.currentTimeMillis())
                .config(config)
                .build();

        // Assign partitions to brokers via controller service
        metadata.setPartitions(controllerService.assignPartitions(
                request.getTopicName(),
                request.getPartitionCount(),
                request.getReplicationFactor()
        ));

        // Persist to database
        TopicEntity entity = TopicEntity.fromMetadata(metadata);
        TopicEntity savedEntity = topicRepository.save(entity);

        log.info("Successfully created topic: {} with {} partitions", request.getTopicName(), request.getPartitionCount());

        // TODO: Notify storage nodes to create partition directories
        // This will be implemented when storage service metadata sync is added

        return savedEntity.toMetadata();
    }

    @Override
    public TopicMetadata getTopicMetadata(String topicName) {
        log.debug("Getting metadata for topic: {}", topicName);

        Optional<TopicEntity> entity = topicRepository.findByTopicName(topicName);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("Topic not found: " + topicName);
        }

        TopicMetadata metadata = entity.get().toMetadata();

        // Get current partition assignments from controller
        metadata.setPartitions(controllerService.assignPartitions(
                topicName,
                metadata.getPartitionCount(),
                metadata.getReplicationFactor()
        ));

        return metadata;
    }

    @Override
    public List<String> listTopics() {
        log.debug("Listing all topics");

        return topicRepository.findAll().stream()
                .map(TopicEntity::getTopicName)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteTopic(String topicName) {
        log.info("Deleting topic: {}", topicName);

        Optional<TopicEntity> entity = topicRepository.findByTopicName(topicName);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("Topic not found: " + topicName);
        }

        // Cleanup partitions via controller service
        controllerService.cleanupTopicPartitions(topicName);

        // Delete from repository
        topicRepository.delete(entity.get());

        log.info("Successfully deleted topic: {}", topicName);

        // TODO: Notify storage nodes to delete data
        // This will be implemented when storage service metadata sync is added
    }

    @Override
    @Transactional
    public void updateTopicMetadata(TopicMetadata metadata) {
        log.debug("Updating metadata for topic: {}", metadata.getTopicName());

        Optional<TopicEntity> existingEntity = topicRepository.findByTopicName(metadata.getTopicName());
        if (existingEntity.isEmpty()) {
            throw new IllegalArgumentException("Topic not found: " + metadata.getTopicName());
        }

        TopicEntity entity = existingEntity.get();
        entity.updateFromMetadata(metadata);

        topicRepository.save(entity);

        log.debug("Successfully updated metadata for topic: {}", metadata.getTopicName());
    }
}
