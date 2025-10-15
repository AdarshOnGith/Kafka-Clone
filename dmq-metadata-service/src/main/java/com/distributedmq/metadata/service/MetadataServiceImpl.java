package com.distributedmq.metadata.service;

import com.distributedmq.common.model.TopicMetadata;
import com.distributedmq.metadata.dto.CreateTopicRequest;
import com.distributedmq.metadata.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
    public TopicMetadata createTopic(CreateTopicRequest request) {
        log.info("Creating topic: {}", request.getTopicName());
        
        // TODO: Check if topic already exists
        // TODO: Create topic configuration
        // TODO: Call controller service to assign partitions to brokers
        // TODO: Create topic metadata
        // TODO: Persist to database
        // TODO: Notify storage nodes to create partition directories
        
        return TopicMetadata.builder()
                .topicName(request.getTopicName())
                .build();
    }

    @Override
    public TopicMetadata getTopicMetadata(String topicName) {
        log.debug("Getting metadata for topic: {}", topicName);
        
        // TODO: Fetch from repository
        // TODO: Convert entity to metadata
        // TODO: Return metadata
        
        return TopicMetadata.builder()
                .topicName(topicName)
                .build();
    }

    @Override
    public List<String> listTopics() {
        // TODO: Query repository for all topics
        // TODO: Return list of topic names
        
        return new ArrayList<>();
    }

    @Override
    public void deleteTopic(String topicName) {
        log.info("Deleting topic: {}", topicName);
        
        // TODO: Validate topic exists
        // TODO: Notify controller to clean up partitions
        // TODO: Delete from repository
        // TODO: Notify storage nodes to delete data
    }

    @Override
    public void updateTopicMetadata(TopicMetadata metadata) {
        log.debug("Updating metadata for topic: {}", metadata.getTopicName());
        
        // TODO: Find existing entity
        // TODO: Update fields
        // TODO: Save to repository
    }
}
