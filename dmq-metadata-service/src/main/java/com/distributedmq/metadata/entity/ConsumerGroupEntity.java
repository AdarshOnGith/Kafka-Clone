package com.distributedmq.metadata.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA Entity for Consumer Groups table.
 */
@Entity
@Table(name = "consumer_groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ConsumerGroupEntity {
    
    @Id
    @Column(name = "group_id", length = 255)
    private String groupId;
    
    @Column(name = "group_state", nullable = false, length = 50)
    private String groupState;
    
    @Column(name = "generation_id", nullable = false)
    private Integer generationId;
    
    @Column(name = "protocol_type", length = 50)
    private String protocolType;
    
    @Column(name = "protocol_name", length = 50)
    private String protocolName;
    
    @Column(name = "leader_member_id", length = 255)
    private String leaderMemberId;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
