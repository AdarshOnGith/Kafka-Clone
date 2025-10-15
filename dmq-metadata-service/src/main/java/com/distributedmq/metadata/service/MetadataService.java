package com.distributedmq.metadata.service;

import com.distributedmq.common.model.TopicMetadata;
import com.distributedmq.metadata.dto.CreateTopicRequest;

import java.util.List;

/**
 * Service interface for Metadata operations
 */
public interface MetadataService {

    /**
     * Create a new topic
     */
    TopicMetadata createTopic(CreateTopicRequest request);

    /**
     * Get topic metadata
     */
    TopicMetadata getTopicMetadata(String topicName);

    /**
     * List all topics
     */
    List<String> listTopics();

    /**
     * Delete a topic
     */
    void deleteTopic(String topicName);

    /**
     * Update topic metadata
     */
    void updateTopicMetadata(TopicMetadata metadata);

    // TODO: Add partition management methods
    // TODO: Add consumer group methods
    // TODO: Add offset management methods
}
