package com.distributedmq.controller.service;

import com.distributedmq.common.constant.DMQConstants;
import com.distributedmq.common.model.StorageNode;
import com.distributedmq.common.proto.*;
import com.distributedmq.controller.election.ControllerLeaderElection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Failure Detection Service.
 * 
 * Implements Flow 3 Step 1 & 2:
 * - Monitor storage node health via Metadata Service
 * - Detect failed nodes (DEAD status)
 * - Identify affected partitions
 * - Trigger leader re-election
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FailureDetectionService {
    
    @GrpcClient("metadata-service")
    private MetadataServiceGrpc.MetadataServiceBlockingStub metadataStub;
    
    private final ControllerLeaderElection leaderElection;
    private final LeaderElectionService electionService;
    
    @Value("${dmq.controller.failure-detection.check-interval-ms:10000}")
    private long checkIntervalMs;
    
    // Cache of previously known node states
    private final Map<String, StorageNode.NodeStatus> nodeStateCache = new ConcurrentHashMap<>();
    
    /**
     * Scheduled task to detect failed nodes.
     * Runs every 10 seconds (configurable).
     * 
     * Flow 3 Step 1: Monitor node health
     */
    @Scheduled(fixedDelayString = "${dmq.controller.failure-detection.check-interval-ms:10000}")
    public void detectFailures() {
        // Only leader performs failure detection
        if (!leaderElection.isLeader()) {
            log.trace("Not leader, skipping failure detection");
            return;
        }
        
        log.debug("Running failure detection check");
        
        try {
            // Get all storage nodes from Metadata Service
            // Note: In a real implementation, we'd have a gRPC method to get all nodes
            // For now, we'll check specific statuses
            
            List<StorageNode> deadNodes = getNodesByStatus(DMQConstants.NODE_STATE_DEAD);
            List<StorageNode> suspectNodes = getNodesByStatus(DMQConstants.NODE_STATE_SUSPECT);
            
            // Process DEAD nodes
            for (StorageNode node : deadNodes) {
                handleNodeFailure(node);
            }
            
            // Log SUSPECT nodes for monitoring
            if (!suspectNodes.isEmpty()) {
                log.warn("Found {} SUSPECT nodes: {}", 
                        suspectNodes.size(),
                        suspectNodes.stream().map(StorageNode::getNodeId).collect(Collectors.toList()));
            }
            
        } catch (Exception e) {
            log.error("Error in failure detection", e);
        }
    }
    
    /**
     * Handle a failed storage node.
     * 
     * Flow 3 Step 2: Identify affected partitions
     */
    private void handleNodeFailure(StorageNode node) {
        String nodeId = node.getNodeId();
        
        // Check if we already processed this failure
        StorageNode.NodeStatus previousStatus = nodeStateCache.get(nodeId);
        if (previousStatus == StorageNode.NodeStatus.DEAD) {
            return; // Already handled
        }
        
        log.warn("⚠️ Detected DEAD node: {} (previous status: {})", nodeId, previousStatus);
        nodeStateCache.put(nodeId, StorageNode.NodeStatus.DEAD);
        
        // Find all partitions where this node was the leader
        List<PartitionInfo> affectedPartitions = findPartitionsWithLeader(nodeId);
        
        if (affectedPartitions.isEmpty()) {
            log.info("Node {} has no partitions as leader, no action needed", nodeId);
            return;
        }
        
        log.warn("Node {} was leader for {} partitions, triggering re-election", 
                nodeId, affectedPartitions.size());
        
        // Trigger leader re-election for each affected partition
        for (PartitionInfo partition : affectedPartitions) {
            try {
                electionService.electNewLeader(partition.topic, partition.partition);
            } catch (Exception e) {
                log.error("Failed to elect new leader for partition {}-{}", 
                        partition.topic, partition.partition, e);
            }
        }
    }
    
    /**
     * Get nodes by status from Metadata Service.
     */
    private List<StorageNode> getNodesByStatus(String status) {
        try {
            // In a real implementation, we'd have a specific gRPC method
            // For now, we'll return empty list and rely on Metadata Service's
            // scheduled failure detection
            
            // TODO: Implement GetNodesByStatus RPC method
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Failed to get nodes by status: {}", status, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find all partitions where the given node is the leader.
     */
    private List<PartitionInfo> findPartitionsWithLeader(String nodeId) {
        List<PartitionInfo> result = new ArrayList<>();
        
        try {
            // Query all topics and partitions from Metadata Service
            // For each partition, check if nodeId is the leader
            
            // TODO: Implement comprehensive partition query
            // For now, this is a placeholder
            
            log.debug("Found {} partitions with leader {}", result.size(), nodeId);
            
        } catch (Exception e) {
            log.error("Failed to find partitions for leader: {}", nodeId, e);
        }
        
        return result;
    }
    
    /**
     * Manual trigger for failure detection (for testing).
     */
    public void triggerFailureDetection() {
        log.info("Manually triggering failure detection");
        detectFailures();
    }
    
    /**
     * Reset node state cache (for testing).
     */
    public void resetCache() {
        nodeStateCache.clear();
        log.info("Node state cache cleared");
    }
    
    /**
     * Internal class to hold partition information.
     */
    private static class PartitionInfo {
        String topic;
        int partition;
        String currentLeader;
        List<String> replicas;
        
        PartitionInfo(String topic, int partition, String currentLeader, List<String> replicas) {
            this.topic = topic;
            this.partition = partition;
            this.currentLeader = currentLeader;
            this.replicas = replicas;
        }
    }
}
