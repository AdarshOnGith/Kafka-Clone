package com.distributedmq.metadata.dto;

import com.distributedmq.common.model.TopicMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for topic metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicMetadataResponse {
    
    private String topicName;
    private Integer partitionCount;
    private Integer replicationFactor;
    private Long createdAt;

    public static TopicMetadataResponse from(TopicMetadata metadata) {
        return TopicMetadataResponse.builder()
                .topicName(metadata.getTopicName())
                .partitionCount(metadata.getPartitionCount())
                .replicationFactor(metadata.getReplicationFactor())
                .createdAt(metadata.getCreatedAt())
                .build();
    }
}
