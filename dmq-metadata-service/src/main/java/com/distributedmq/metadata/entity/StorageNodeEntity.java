package com.distributedmq.metadata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA Entity for Storage Nodes table.
 */
@Entity
@Table(name = "storage_nodes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageNodeEntity {
    
    @Id
    @Column(name = "node_id", length = 255)
    private String nodeId;
    
    @Column(name = "host", nullable = false, length = 255)
    private String host;
    
    @Column(name = "port", nullable = false)
    private Integer port;
    
    @Column(name = "rack_id", length = 255)
    private String rackId;
    
    @Column(name = "status", nullable = false, length = 50)
    private String status;
    
    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;
    
    @Column(name = "last_heartbeat", nullable = false)
    private LocalDateTime lastHeartbeat;
    
    @Column(name = "heartbeat_count", nullable = false)
    private Long heartbeatCount;
}
