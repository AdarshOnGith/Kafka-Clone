package com.distributedmq.metadata.service;

import com.distributedmq.common.constant.DMQConstants;
import com.distributedmq.common.exception.ErrorCode;
import com.distributedmq.common.exception.MetadataException;
import com.distributedmq.common.model.ConsumerOffset;
import com.distributedmq.metadata.entity.ConsumerGroupEntity;
import com.distributedmq.metadata.entity.ConsumerOffsetEntity;
import com.distributedmq.metadata.entity.TopicEntity;
import com.distributedmq.metadata.repository.ConsumerGroupRepository;
import com.distributedmq.metadata.repository.ConsumerOffsetRepository;
import com.distributedmq.metadata.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing consumer offsets.
 * Implements Flow 2 Step 6: Offset Commits
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerOffsetService {
    
    private final ConsumerOffsetRepository offsetRepository;
    private final ConsumerGroupRepository groupRepository;
    private final TopicRepository topicRepository;
    
    /**
     * Get consumer offset for a specific partition (cached for 30 seconds).
     * Used by Consumer Egress Service to determine where to start reading.
     */
    @Cacheable(value = "consumerOffsets", key = "#groupId + ':' + #topicName + ':' + #partitionNumber")
    @Transactional(readOnly = true)
    public ConsumerOffset getConsumerOffset(String groupId, String topicName, int partitionNumber) {
        log.debug("Fetching offset for group={}, topic={}, partition={}", groupId, topicName, partitionNumber);
        
        TopicEntity topic = topicRepository.findByTopicName(topicName)
                .orElseThrow(() -> new MetadataException(ErrorCode.TOPIC_NOT_FOUND));
        
        return offsetRepository
                .findByGroupIdAndTopicIdAndPartitionNumber(groupId, topic.getTopicId(), partitionNumber)
                .map(entity -> ConsumerOffset.builder()
                        .groupId(entity.getGroupId())
                        .topicName(topicName)
                        .partition(entity.getPartitionNumber())
                        .offset(entity.getOffsetValue())
                        .leaderEpoch(entity.getLeaderEpoch())
                        .metadata(entity.getMetadata())
                        .timestamp(entity.getCommitTimestamp())
                        .build())
                .orElse(ConsumerOffset.builder()
                        .groupId(groupId)
                        .topicName(topicName)
                        .partition(partitionNumber)
                        .offset(0L) // Start from beginning if no offset exists
                        .build());
    }
    
    /**
     * Get all offsets for a consumer group on a topic.
     */
    @Transactional(readOnly = true)
    public List<ConsumerOffset> getConsumerOffsets(String groupId, String topicName) {
        log.debug("Fetching all offsets for group={}, topic={}", groupId, topicName);
        
        TopicEntity topic = topicRepository.findByTopicName(topicName)
                .orElseThrow(() -> new MetadataException(ErrorCode.TOPIC_NOT_FOUND));
        
        return offsetRepository.findByGroupIdAndTopicId(groupId, topic.getTopicId())
                .stream()
                .map(entity -> ConsumerOffset.builder()
                        .groupId(entity.getGroupId())
                        .topicName(topicName)
                        .partition(entity.getPartitionNumber())
                        .offset(entity.getOffsetValue())
                        .leaderEpoch(entity.getLeaderEpoch())
                        .metadata(entity.getMetadata())
                        .timestamp(entity.getCommitTimestamp())
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * Commit offset for a consumer group.
     * Implements Flow 2 Step 6: Consumer commits offset after processing.
     */
    @CacheEvict(value = "consumerOffsets", key = "#offset.groupId + ':' + #offset.topicName + ':' + #offset.partition")
    @Transactional
    public void commitOffset(ConsumerOffset offset) {
        log.debug("Committing offset: group={}, topic={}, partition={}, offset={}", 
                offset.getGroupId(), offset.getTopicName(), offset.getPartition(), offset.getOffset());
        
        // Ensure consumer group exists
        groupRepository.findById(offset.getGroupId())
                .orElseGet(() -> {
                    ConsumerGroupEntity newGroup = ConsumerGroupEntity.builder()
                            .groupId(offset.getGroupId())
                            .groupState(DMQConstants.GROUP_STATE_STABLE)
                            .generationId(0)
                            .build();
                    return groupRepository.save(newGroup);
                });
        
        TopicEntity topic = topicRepository.findByTopicName(offset.getTopicName())
                .orElseThrow(() -> new MetadataException(ErrorCode.TOPIC_NOT_FOUND));
        
        ConsumerOffsetEntity entity = offsetRepository
                .findByGroupIdAndTopicIdAndPartitionNumber(
                        offset.getGroupId(), 
                        topic.getTopicId(), 
                        offset.getPartition()
                )
                .orElse(ConsumerOffsetEntity.builder()
                        .groupId(offset.getGroupId())
                        .topicId(topic.getTopicId())
                        .partitionNumber(offset.getPartition())
                        .build());
        
        entity.setOffsetValue(offset.getOffset());
        entity.setLeaderEpoch(offset.getLeaderEpoch() != null ? offset.getLeaderEpoch() : 0);
        entity.setMetadata(offset.getMetadata());
        entity.setCommitTimestamp(LocalDateTime.now());
        
        offsetRepository.save(entity);
        
        log.info("Offset committed successfully");
    }
    
    /**
     * Commit multiple offsets in a single transaction.
     */
    @CacheEvict(value = "consumerOffsets", allEntries = true)
    @Transactional
    public void commitOffsets(List<ConsumerOffset> offsets) {
        log.debug("Committing {} offsets", offsets.size());
        
        for (ConsumerOffset offset : offsets) {
            commitOffset(offset);
        }
    }
    
    /**
     * Scheduled task to clean up expired offsets.
     * Runs every 24 hours.
     */
    @Scheduled(cron = "0 0 0 * * ?") // Midnight every day
    @Transactional
    public void cleanupExpiredOffsets() {
        log.info("Starting cleanup of expired offsets");
        
        int deleted = offsetRepository.deleteExpiredOffsets(LocalDateTime.now());
        
        log.info("Cleaned up {} expired offsets", deleted);
    }
}
