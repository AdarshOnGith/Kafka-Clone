package com.distributedmq.controller.service;

import com.distributedmq.common.proto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Leader Election Service.
 * 
 * Implements Flow 3 Step 3:
 * - Select new leader from ISR
 * - Use configured election algorithm
 * - Fallback to any available replica if ISR is empty
 * 
 * Election Algorithms:
 * 1. first-available: Pick first replica in ISR list
 * 2. least-loaded: Pick replica with least partitions (TODO)
 * 3. rack-aware: Prefer different rack than current leader (TODO)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderElectionService {
    
    @GrpcClient("metadata-service")
    private MetadataServiceGrpc.MetadataServiceBlockingStub metadataStub;
    
    private final MetadataUpdateService metadataUpdateService;
    
    @Value("${dmq.controller.leader-election.algorithm:first-available}")
    private String electionAlgorithm;
    
    @Value("${dmq.controller.leader-election.min-isr-required:2}")
    private int minIsrRequired;
    
    @Value("${dmq.controller.leader-election.preferred-replica-index:0}")
    private int preferredReplicaIndex;
    
    /**
     * Elect a new leader for the partition.
     * 
     * Flow 3 Step 3: Controller picks new leader from ISR
     */
    public void electNewLeader(String topic, int partition) {
        log.info("🗳️ Starting leader election for partition {}-{}", topic, partition);
        
        try {
            // Get partition metadata
            PartitionMetadataRequest request = PartitionMetadataRequest.newBuilder()
                    .setTopic(topic)
                    .setPartition(partition)
                    .build();
            
            PartitionMetadataResponse metadata = metadataStub.getPartitionMetadata(request);
            
            // Get current ISR
            List<String> isr = new ArrayList<>(metadata.getInSyncReplicasList());
            List<String> allReplicas = new ArrayList<>(metadata.getReplicasList());
            
            log.info("Partition {}-{}: ISR={}, AllReplicas={}, CurrentLeader={}", 
                    topic, partition, isr, allReplicas, metadata.getLeaderNodeId());
            
            // Remove current (failed) leader from ISR
            isr.remove(metadata.getLeaderNodeId());
            
            if (isr.isEmpty()) {
                log.warn("ISR is empty for partition {}-{}, using all replicas", topic, partition);
                isr = allReplicas.stream()
                        .filter(r -> !r.equals(metadata.getLeaderNodeId()))
                        .collect(Collectors.toList());
            }
            
            if (isr.isEmpty()) {
                log.error("❌ No available replicas for partition {}-{}, partition is OFFLINE", 
                        topic, partition);
                return;
            }
            
            // Select new leader based on algorithm
            String newLeader = selectNewLeader(isr, allReplicas);
            int newEpoch = metadata.getLeaderEpoch() + 1;
            
            log.info("✅ Selected new leader for {}-{}: {} (epoch: {})", 
                    topic, partition, newLeader, newEpoch);
            
            // Update metadata
            metadataUpdateService.updatePartitionLeader(
                    topic, 
                    partition, 
                    newLeader, 
                    getNodeAddress(newLeader),
                    newEpoch,
                    isr
            );
            
        } catch (Exception e) {
            log.error("Failed to elect new leader for partition {}-{}", topic, partition, e);
            throw new RuntimeException("Leader election failed", e);
        }
    }
    
    /**
     * Select new leader based on configured algorithm.
     */
    private String selectNewLeader(List<String> isr, List<String> allReplicas) {
        switch (electionAlgorithm.toLowerCase()) {
            case "first-available":
                return selectFirstAvailable(isr);
            
            case "least-loaded":
                return selectLeastLoaded(isr);
            
            case "rack-aware":
                return selectRackAware(isr, allReplicas);
            
            default:
                log.warn("Unknown election algorithm: {}, using first-available", electionAlgorithm);
                return selectFirstAvailable(isr);
        }
    }
    
    /**
     * Algorithm: first-available
     * Simply picks the first replica in ISR list.
     */
    private String selectFirstAvailable(List<String> isr) {
        if (isr.isEmpty()) {
            throw new IllegalArgumentException("ISR list is empty");
        }
        
        // Prefer replica at preferred index if available
        if (preferredReplicaIndex < isr.size()) {
            return isr.get(preferredReplicaIndex);
        }
        
        return isr.get(0);
    }
    
    /**
     * Algorithm: least-loaded
     * Picks replica with least number of partitions as leader.
     * TODO: Implement by querying partition counts per node.
     */
    private String selectLeastLoaded(List<String> isr) {
        log.warn("least-loaded algorithm not yet implemented, using first-available");
        return selectFirstAvailable(isr);
    }
    
    /**
     * Algorithm: rack-aware
     * Prefers replica in different rack for fault tolerance.
     * TODO: Implement rack awareness.
     */
    private String selectRackAware(List<String> isr, List<String> allReplicas) {
        log.warn("rack-aware algorithm not yet implemented, using first-available");
        return selectFirstAvailable(isr);
    }
    
    /**
     * Get node address for a given node ID.
     * TODO: Query from Metadata Service.
     */
    private String getNodeAddress(String nodeId) {
        // For now, assume standard format: nodeId:port
        // In production, query from Metadata Service
        return nodeId + ":9092";
    }
}
