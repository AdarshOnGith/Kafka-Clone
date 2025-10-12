package com.distributedmq.metadata.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA Entity for Partition Replicas table.
 */
@Entity
@Table(name = "partition_replicas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PartitionReplicaEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "replica_id")
    private Integer replicaId;
    
    @Column(name = "partition_id", nullable = false)
    private Integer partitionId;
    
    @Column(name = "node_id", nullable = false, length = 255)
    private String nodeId;
    
    @Column(name = "node_address", nullable = false, length = 255)
    private String nodeAddress;
    
    @Column(name = "is_in_sync", nullable = false)
    private Boolean isInSync;
    
    @Column(name = "last_caught_up_timestamp")
    private LocalDateTime lastCaughtUpTimestamp;
    
    @Column(name = "lag_messages", nullable = false)
    private Long lagMessages;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
