package com.distributedmq.metadata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA Entity for Consumer Offsets table.
 */
@Entity
@Table(name = "consumer_offsets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerOffsetEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "offset_id")
    private Integer offsetId;
    
    @Column(name = "group_id", nullable = false, length = 255)
    private String groupId;
    
    @Column(name = "topic_id", nullable = false)
    private Integer topicId;
    
    @Column(name = "partition_number", nullable = false)
    private Integer partitionNumber;
    
    @Column(name = "offset_value", nullable = false)
    private Long offsetValue;
    
    @Column(name = "leader_epoch", nullable = false)
    private Integer leaderEpoch;
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    @Column(name = "commit_timestamp", nullable = false)
    private LocalDateTime commitTimestamp;
    
    @Column(name = "expire_timestamp")
    private LocalDateTime expireTimestamp;
}
