package com.distributedmq.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a storage node in the cluster.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageNode {
    
    /**
     * Unique node ID.
     */
    private String nodeId;
    
    /**
     * Node hostname.
     */
    private String host;
    
    /**
     * Node port.
     */
    private int port;
    
    /**
     * Node status.
     */
    private NodeStatus status;
    
    /**
     * Timestamp when node registered.
     */
    private long registrationTime;
    
    /**
     * Last heartbeat timestamp.
     */
    private long lastHeartbeat;
    
    /**
     * Rack ID for rack-aware replication (optional).
     */
    private String rackId;
    
    /**
     * Get full address (host:port).
     */
    public String getAddress() {
        return host + ":" + port;
    }
    
    /**
     * Storage node status.
     */
    public enum NodeStatus {
        ALIVE,      // Node is healthy and accepting requests
        SUSPECT,    // Node missed heartbeats, might be dead
        DEAD,       // Node confirmed dead
        DRAINING    // Node is being gracefully shut down
    }
}
