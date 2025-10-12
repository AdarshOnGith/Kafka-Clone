package com.distributedmq.metadata.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA Entity for Partitions table.
 */
@Entity
@Table(name = "partitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PartitionEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "partition_id")
    private Integer partitionId;
    
    @Column(name = "topic_id", nullable = false)
    private Integer topicId;
    
    @Column(name = "partition_number", nullable = false)
    private Integer partitionNumber;
    
    @Column(name = "leader_node_id", length = 255)
    private String leaderNodeId;
    
    @Column(name = "leader_node_address", length = 255)
    private String leaderNodeAddress;
    
    @Column(name = "leader_epoch", nullable = false)
    private Integer leaderEpoch;
    
    @Column(name = "high_water_mark", nullable = false)
    private Long highWaterMark;
    
    @Column(name = "log_end_offset", nullable = false)
    private Long logEndOffset;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
