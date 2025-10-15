package com.distributedmq.metadata.entity;

import com.distributedmq.common.model.TopicMetadata;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA Entity for Topic
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "topics")
public class TopicEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String topicName;

    @Column(nullable = false)
    private Integer partitionCount;

    @Column(nullable = false)
    private Integer replicationFactor;

    @Column(nullable = false)
    private Long createdAt;

    // Configuration stored as JSON or separate table
    @Column(columnDefinition = "TEXT")
    private String configJson;

    // TODO: Add method to convert TopicMetadata to TopicEntity
    public static TopicEntity fromMetadata(TopicMetadata metadata) {
        // TODO: Implement conversion logic
        // TODO: Serialize TopicConfig to JSON string
        return new TopicEntity();
    }

    // TODO: Add method to convert TopicEntity to TopicMetadata
    public TopicMetadata toMetadata() {
        // TODO: Implement conversion logic
        // TODO: Deserialize JSON string to TopicConfig
        return TopicMetadata.builder().build();
    }

    // TODO: Add method to update entity from metadata
    public void updateFromMetadata(TopicMetadata metadata) {
        // TODO: Update entity fields from metadata
        // TODO: Update configJson from TopicConfig
    }
}
