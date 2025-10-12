package com.distributedmq.metadata.service;

import com.distributedmq.common.constant.DMQConstants;
import com.distributedmq.common.model.StorageNode;
import com.distributedmq.metadata.entity.StorageNodeEntity;
import com.distributedmq.metadata.repository.StorageNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing storage node registration and health monitoring.
 * Implements Flow 3 Step 1: Failure Detection via heartbeat monitoring.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageNodeService {
    
    private final StorageNodeRepository nodeRepository;
    
    @Value("${dmq.metadata.heartbeat.suspect-threshold-seconds:15}")
    private int suspectThresholdSeconds;
    
    @Value("${dmq.metadata.heartbeat.dead-threshold-seconds:60}")
    private int deadThresholdSeconds;
    
    /**
     * Register a new storage node.
     */
    @Transactional
    public void registerNode(String nodeId, String host, int port, String rackId) {
        log.info("Registering storage node: nodeId={}, host={}, port={}", nodeId, host, port);
        
        StorageNodeEntity entity = nodeRepository.findById(nodeId)
                .orElse(StorageNodeEntity.builder()
                        .nodeId(nodeId)
                        .host(host)
                        .port(port)
                        .rackId(rackId)
                        .status(DMQConstants.NODE_STATE_ALIVE)
                        .registeredAt(LocalDateTime.now())
                        .heartbeatCount(0L)
                        .build());
        
        entity.setHost(host);
        entity.setPort(port);
        entity.setRackId(rackId);
        entity.setStatus(DMQConstants.NODE_STATE_ALIVE);
        entity.setLastHeartbeat(LocalDateTime.now());
        
        nodeRepository.save(entity);
        
        log.info("Storage node registered successfully");
    }
    
    /**
     * Process heartbeat from storage node.
     */
    @Transactional
    public void processHeartbeat(String nodeId) {
        log.trace("Processing heartbeat for node: {}", nodeId);
        
        StorageNodeEntity entity = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not registered: " + nodeId));
        
        entity.setLastHeartbeat(LocalDateTime.now());
        entity.setHeartbeatCount(entity.getHeartbeatCount() + 1);
        
        // If node was SUSPECT or DEAD, mark as ALIVE
        if (!DMQConstants.NODE_STATE_ALIVE.equals(entity.getStatus())) {
            log.info("Node {} recovered from {} state", nodeId, entity.getStatus());
            entity.setStatus(DMQConstants.NODE_STATE_ALIVE);
        }
        
        nodeRepository.save(entity);
    }
    
    /**
     * Get all healthy nodes.
     */
    @Transactional(readOnly = true)
    public List<StorageNode> getHealthyNodes() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(suspectThresholdSeconds);
        
        return nodeRepository.findHealthyNodes(threshold)
                .stream()
                .map(this::toStorageNode)
                .collect(Collectors.toList());
    }
    
    /**
     * Get nodes by status.
     */
    @Transactional(readOnly = true)
    public List<StorageNode> getNodesByStatus(String status) {
        return nodeRepository.findByStatus(status)
                .stream()
                .map(this::toStorageNode)
                .collect(Collectors.toList());
    }
    
    /**
     * Scheduled task to detect failed nodes.
     * Runs every 10 seconds.
     * Implements Flow 3 Step 1: Controller detects failures.
     */
    @Scheduled(fixedRate = 10000) // Every 10 seconds
    @Transactional
    public void detectFailures() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime suspectThreshold = now.minusSeconds(suspectThresholdSeconds);
        LocalDateTime deadThreshold = now.minusSeconds(deadThresholdSeconds);
        
        // Find nodes that haven't sent heartbeat recently
        List<StorageNodeEntity> suspectNodes = nodeRepository.findSuspectNodes(suspectThreshold);
        
        for (StorageNodeEntity node : suspectNodes) {
            LocalDateTime lastHeartbeat = node.getLastHeartbeat();
            
            if (lastHeartbeat.isBefore(deadThreshold)) {
                // Mark as DEAD if no heartbeat for deadThresholdSeconds
                if (!DMQConstants.NODE_STATE_DEAD.equals(node.getStatus())) {
                    log.warn("Marking node {} as DEAD (last heartbeat: {})", 
                            node.getNodeId(), lastHeartbeat);
                    node.setStatus(DMQConstants.NODE_STATE_DEAD);
                    nodeRepository.save(node);
                }
            } else if (lastHeartbeat.isBefore(suspectThreshold)) {
                // Mark as SUSPECT if no heartbeat for suspectThresholdSeconds
                if (DMQConstants.NODE_STATE_ALIVE.equals(node.getStatus())) {
                    log.warn("Marking node {} as SUSPECT (last heartbeat: {})", 
                            node.getNodeId(), lastHeartbeat);
                    node.setStatus(DMQConstants.NODE_STATE_SUSPECT);
                    nodeRepository.save(node);
                }
            }
        }
    }
    
    /**
     * Deregister a node (graceful shutdown).
     */
    @Transactional
    public void deregisterNode(String nodeId) {
        log.info("Deregistering storage node: {}", nodeId);
        
        StorageNodeEntity entity = nodeRepository.findById(nodeId).orElse(null);
        if (entity != null) {
            entity.setStatus(DMQConstants.NODE_STATE_DEAD);
            nodeRepository.save(entity);
        }
    }
    
    private StorageNode toStorageNode(StorageNodeEntity entity) {
        return StorageNode.builder()
                .nodeId(entity.getNodeId())
                .host(entity.getHost())
                .port(entity.getPort())
                .rackId(entity.getRackId())
                .status(StorageNode.NodeStatus.valueOf(entity.getStatus()))
                .lastHeartbeat(entity.getLastHeartbeat())
                .heartbeatCount(entity.getHeartbeatCount())
                .build();
    }
}
