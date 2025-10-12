package com.distributedmq.controller.service;

import com.distributedmq.common.proto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Metadata Update Service.
 * 
 * Implements Flow 3 Step 4:
 * - Update partition leader in Metadata Service
 * - Update ISR membership
 * - Increment leader epoch
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataUpdateService {
    
    @GrpcClient("metadata-service")
    private MetadataServiceGrpc.MetadataServiceBlockingStub metadataStub;
    
    /**
     * Update partition leader after election.
     * 
     * Flow 3 Step 4: Controller updates partition leader in Metadata Service
     */
    public void updatePartitionLeader(
            String topic,
            int partition,
            String newLeaderNodeId,
            String newLeaderAddress,
            int newEpoch,
            List<String> newIsr) {
        
        log.info("Updating partition leader: topic={}, partition={}, newLeader={}, epoch={}, isr={}", 
                topic, partition, newLeaderNodeId, newEpoch, newIsr);
        
        try {
            // Build update request
            UpdateLeaderRequest request = UpdateLeaderRequest.newBuilder()
                    .setTopic(topic)
                    .setPartition(partition)
                    .setNewLeaderNodeId(newLeaderNodeId)
                    .setNewLeaderAddress(newLeaderAddress)
                    .setNewLeaderEpoch(newEpoch)
                    .addAllNewIsr(newIsr)
                    .build();
            
            // Send update to Metadata Service
            UpdateLeaderResponse response = metadataStub.updatePartitionLeader(request);
            
            if (response.getSuccess()) {
                log.info("✅ Successfully updated partition leader for {}-{}", topic, partition);
            } else {
                log.error("❌ Failed to update partition leader: errorCode={}, message={}", 
                        response.getErrorCode(), response.getErrorMessage());
                throw new RuntimeException("Failed to update partition leader: " + response.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("Exception updating partition leader for {}-{}", topic, partition, e);
            throw new RuntimeException("Failed to update partition leader", e);
        }
    }
    
    /**
     * Update ISR membership for a partition.
     */
    public void updateInSyncReplicas(String topic, int partition, List<String> isr) {
        log.info("Updating ISR for partition {}-{}: {}", topic, partition, isr);
        
        try {
            // TODO: Implement UpdateISR RPC method in Metadata Service
            // For now, ISR is updated as part of leader update
            
            log.debug("ISR update completed for {}-{}", topic, partition);
            
        } catch (Exception e) {
            log.error("Failed to update ISR for {}-{}", topic, partition, e);
        }
    }
}
