package com.distributedmq.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a topic
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTopicRequest {
    
    @NotBlank(message = "Topic name is required")
    private String topicName;
    
    @Min(value = 1, message = "Partition count must be at least 1")
    private Integer partitionCount;
    
    @Min(value = 1, message = "Replication factor must be at least 1")
    private Integer replicationFactor;
    
    private Long retentionMs;
    private Long retentionBytes;
    private Integer segmentBytes;
    private String compressionType;
    private Integer minInsyncReplicas;
}
