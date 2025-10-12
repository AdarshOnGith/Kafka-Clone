package com.distributedmq.storage.replication;

import com.distributedmq.common.proto.*;
import com.distributedmq.storage.model.LogRecord;
import com.distributedmq.storage.wal.PartitionLog;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;

/**
 * Replication Manager - handles leader-to-follower replication.
 * 
 * Responsibilities:
 * - Push records to follower replicas
 * - Track replica lag (offset and time)
 * - Maintain In-Sync Replica (ISR) set
 * - Update High Water Mark based on ISR
 * 
 * Implements Flow 1 Step 6: Replication
 */
@Slf4j
@Service
public class ReplicationManager {
    
    private final PartitionLog partitionLog;
    
    @Value("${dmq.storage.replication.replica-lag-time-max-ms:10000}")
    private long replicaLagTimeMaxMs;
    
    @Value("${dmq.storage.replication.replica-lag-max-messages:4000}")
    private long replicaLagMaxMessages;
    
    @Value("${dmq.storage.isr.check-interval-ms:5000}")
    private long isrCheckIntervalMs;
    
    // Map: topic-partition -> ReplicaState
    private final Map<String, PartitionReplicaState> replicaStates = new ConcurrentHashMap<>();
    
    // Executor for async replication
    private final ExecutorService replicationExecutor = Executors.newFixedThreadPool(10);
    
    // Scheduled executor for ISR checks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public ReplicationManager(PartitionLog partitionLog) {
        this.partitionLog = partitionLog;
        
        // Start ISR monitoring
        scheduler.scheduleAtFixedRate(
                this::checkInSyncReplicas,
                isrCheckIntervalMs,
                isrCheckIntervalMs,
                TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Replicate records to follower nodes.
     * This is called after leader appends records to its log.
     */
    public CompletableFuture<Void> replicateToFollowers(
            String topic, 
            int partition, 
            List<LogRecord> records,
            List<String> replicaAddresses,
            int requiredAcks) {
        
        if (replicaAddresses.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        String key = getPartitionKey(topic, partition);
        PartitionReplicaState state = getOrCreateReplicaState(key, topic, partition, replicaAddresses);
        
        // Send records to each follower in parallel
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (String replicaAddress : replicaAddresses) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    replicateToReplica(topic, partition, records, replicaAddress, state);
                } catch (Exception e) {
                    log.error("Replication failed to {}: {}", replicaAddress, e.getMessage());
                    state.markReplicaFailed(replicaAddress);
                }
            }, replicationExecutor);
            
            futures.add(future);
        }
        
        // Wait for required acks
        if (requiredAcks == -1) {
            // Wait for all ISR replicas
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        } else if (requiredAcks > 1) {
            // Wait for specified number of acks
            return waitForAcks(futures, requiredAcks - 1); // -1 because leader already has it
        } else {
            // Leader-only ack (requiredAcks == 1)
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * Get In-Sync Replicas for a partition.
     */
    public List<String> getInSyncReplicas(String topic, int partition) {
        String key = getPartitionKey(topic, partition);
        PartitionReplicaState state = replicaStates.get(key);
        return state != null ? new ArrayList<>(state.getInSyncReplicas()) : new ArrayList<>();
    }
    
    /**
     * Update High Water Mark based on ISR.
     * HWM = minimum LEO among all ISR replicas.
     */
    public void updateHighWaterMark(String topic, int partition) {
        String key = getPartitionKey(topic, partition);
        PartitionReplicaState state = replicaStates.get(key);
        
        if (state == null) {
            return;
        }
        
        long leaderLeo = partitionLog.getLogEndOffset(topic, partition);
        long minLeo = leaderLeo;
        
        // Find minimum LEO among ISR
        for (ReplicaInfo replica : state.getReplicas()) {
            if (state.isInSync(replica.getAddress())) {
                minLeo = Math.min(minLeo, replica.getLastFetchedOffset());
            }
        }
        
        // Update HWM
        partitionLog.updateHighWaterMark(topic, partition, minLeo);
        
        log.debug("Updated HWM for {}-{}: {}", topic, partition, minLeo);
    }
    
    // ========== Private Methods ==========
    
    private void replicateToReplica(
            String topic, 
            int partition, 
            List<LogRecord> records,
            String replicaAddress,
            PartitionReplicaState state) {
        
        // Create gRPC stub
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget(replicaAddress)
                .usePlaintext()
                .build();
        
        try {
            StorageServiceGrpc.StorageServiceBlockingStub stub = 
                    StorageServiceGrpc.newBlockingStub(channel);
            
            // Build replication request
            ReplicationRequest.Builder requestBuilder = ReplicationRequest.newBuilder()
                    .setTopic(topic)
                    .setPartition(partition)
                    .setLeaderEpoch(records.get(0).getLeaderEpoch())
                    .setBaseOffset(records.get(0).getOffset());
            
            for (LogRecord record : records) {
                com.distributedmq.common.proto.Record protoRecord = 
                        com.distributedmq.common.proto.Record.newBuilder()
                        .setKey(record.getKey() != null ? record.getKey() : "")
                        .setValue(com.google.protobuf.ByteString.copyFrom(record.getValue()))
                        .setTimestamp(record.getTimestamp())
                        .putAllHeaders(record.getHeaders() != null ? record.getHeaders() : Map.of())
                        .build();
                
                requestBuilder.addRecords(protoRecord);
            }
            
            // Send replication request
            ReplicationResponse response = stub.replicateRecords(requestBuilder.build());
            
            if (response.getSuccess()) {
                state.updateReplicaOffset(replicaAddress, response.getLastOffset());
                log.trace("Replicated {} records to {}", records.size(), replicaAddress);
            } else {
                log.warn("Replication to {} failed: error code {}", 
                        replicaAddress, response.getErrorCode());
                state.markReplicaFailed(replicaAddress);
            }
            
        } finally {
            channel.shutdown();
        }
    }
    
    private CompletableFuture<Void> waitForAcks(List<CompletableFuture<Void>> futures, int required) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> result.complete(null))
                .exceptionally(ex -> {
                    // Even if some fail, check if we have enough acks
                    long successCount = futures.stream().filter(f -> !f.isCompletedExceptionally()).count();
                    if (successCount >= required) {
                        result.complete(null);
                    } else {
                        result.completeExceptionally(new Exception("Insufficient replicas: " + successCount));
                    }
                    return null;
                });
        
        return result;
    }
    
    private void checkInSyncReplicas() {
        long now = System.currentTimeMillis();
        
        for (Map.Entry<String, PartitionReplicaState> entry : replicaStates.entrySet()) {
            String partitionKey = entry.getKey();
            PartitionReplicaState state = entry.getValue();
            
            String[] parts = partitionKey.split("-");
            String topic = parts[0];
            int partition = Integer.parseInt(parts[1]);
            
            long leaderLeo = partitionLog.getLogEndOffset(topic, partition);
            
            for (ReplicaInfo replica : state.getReplicas()) {
                long timeLag = now - replica.getLastFetchTime();
                long offsetLag = leaderLeo - replica.getLastFetchedOffset();
                
                boolean isInSync = timeLag <= replicaLagTimeMaxMs && offsetLag <= replicaLagMaxMessages;
                
                if (isInSync && !state.isInSync(replica.getAddress())) {
                    log.info("Replica {} added to ISR for {}-{}", 
                            replica.getAddress(), topic, partition);
                    state.addToInSyncReplicas(replica.getAddress());
                } else if (!isInSync && state.isInSync(replica.getAddress())) {
                    log.warn("Replica {} removed from ISR for {}-{} (timeLag={}ms, offsetLag={})", 
                            replica.getAddress(), topic, partition, timeLag, offsetLag);
                    state.removeFromInSyncReplicas(replica.getAddress());
                }
            }
            
            // Update HWM after ISR check
            updateHighWaterMark(topic, partition);
        }
    }
    
    private PartitionReplicaState getOrCreateReplicaState(
            String key, String topic, int partition, List<String> replicaAddresses) {
        
        return replicaStates.computeIfAbsent(key, k -> {
            PartitionReplicaState state = new PartitionReplicaState(topic, partition);
            for (String address : replicaAddresses) {
                state.addReplica(address);
            }
            return state;
        });
    }
    
    private String getPartitionKey(String topic, int partition) {
        return topic + "-" + partition;
    }
    
    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        replicationExecutor.shutdown();
    }
    
    // ========== Inner Classes ==========
    
    private static class PartitionReplicaState {
        private final String topic;
        private final int partition;
        private final List<ReplicaInfo> replicas = new CopyOnWriteArrayList<>();
        private final Set<String> inSyncReplicas = ConcurrentHashMap.newKeySet();
        
        public PartitionReplicaState(String topic, int partition) {
            this.topic = topic;
            this.partition = partition;
        }
        
        public void addReplica(String address) {
            ReplicaInfo info = new ReplicaInfo(address);
            replicas.add(info);
            inSyncReplicas.add(address);
        }
        
        public void updateReplicaOffset(String address, long offset) {
            for (ReplicaInfo replica : replicas) {
                if (replica.getAddress().equals(address)) {
                    replica.setLastFetchedOffset(offset);
                    replica.setLastFetchTime(System.currentTimeMillis());
                    break;
                }
            }
        }
        
        public void markReplicaFailed(String address) {
            inSyncReplicas.remove(address);
        }
        
        public boolean isInSync(String address) {
            return inSyncReplicas.contains(address);
        }
        
        public void addToInSyncReplicas(String address) {
            inSyncReplicas.add(address);
        }
        
        public void removeFromInSyncReplicas(String address) {
            inSyncReplicas.remove(address);
        }
        
        public List<ReplicaInfo> getReplicas() {
            return new ArrayList<>(replicas);
        }
        
        public Set<String> getInSyncReplicas() {
            return new HashSet<>(inSyncReplicas);
        }
    }
    
    private static class ReplicaInfo {
        private final String address;
        private volatile long lastFetchedOffset = 0L;
        private volatile long lastFetchTime = System.currentTimeMillis();
        
        public ReplicaInfo(String address) {
            this.address = address;
        }
        
        public String getAddress() {
            return address;
        }
        
        public long getLastFetchedOffset() {
            return lastFetchedOffset;
        }
        
        public void setLastFetchedOffset(long offset) {
            this.lastFetchedOffset = offset;
        }
        
        public long getLastFetchTime() {
            return lastFetchTime;
        }
        
        public void setLastFetchTime(long time) {
            this.lastFetchTime = time;
        }
    }
}
