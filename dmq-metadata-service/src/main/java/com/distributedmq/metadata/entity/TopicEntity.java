package com.distributedmq.metadata.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA Entity for Topics table.
 */
@Entity
@Table(name = "topics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TopicEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "topic_id")
    private Integer topicId;
    
    @Column(name = "topic_name", nullable = false, unique = true, length = 255)
    private String topicName;
    
    @Column(name = "partition_count", nullable = false)
    private Integer partitionCount;
    
    @Column(name = "replication_factor", nullable = false)
    private Integer replicationFactor;
    
    @Column(name = "min_in_sync_replicas", nullable = false)
    private Integer minInSyncReplicas;
    
    @Column(name = "retention_ms", nullable = false)
    private Long retentionMs;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
