package com.distributedmq.storage.service;

import com.distributedmq.common.dto.ConsumeRequest;
import com.distributedmq.common.dto.ConsumeResponse;
import com.distributedmq.common.dto.ProduceRequest;
import com.distributedmq.common.dto.ProduceResponse;
import com.distributedmq.common.model.Message;
import com.distributedmq.storage.config.StorageConfig;
import com.distributedmq.storage.replication.MetadataStore;
import com.distributedmq.storage.replication.ReplicationManager;
import com.distributedmq.storage.wal.WriteAheadLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of StorageService
 * Manages WAL and message persistence
 */

@Slf4j       // Injects log for logging
@Service       // Marks as a Spring component
@RequiredArgsConstructor // Generates constructor for 'final' fields

public class StorageServiceImpl implements StorageService {

    // Map of topic-partition to WAL
    private final Map<String, WriteAheadLog> partitionLogs = new ConcurrentHashMap<>();
    
    private final ReplicationManager replicationManager;
    private final StorageConfig config;
    private final MetadataStore metadataStore;

    @Override
    public ProduceResponse appendMessages(ProduceRequest request) {
        String topicPartition = getTopicPartitionKey(request.getTopic(), request.getPartition());

        log.info("Attempting to append {} messages to {}", 
                request.getMessages().size(), topicPartition);
        
        // Step 1: Validate request and check leadership
        ProduceResponse.ErrorCode validationError = validateProduceRequest(request);
        if (validationError != ProduceResponse.ErrorCode.NONE) {
            return ProduceResponse.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .success(false)
                    .errorCode(validationError)
                    .errorMessage(validationError.getMessage())
                    .build();
        }
        
        if (!isLeaderForPartition(request.getTopic(), request.getPartition())) {
            return ProduceResponse.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .success(false)
                    .errorCode(ProduceResponse.ErrorCode.NOT_LEADER_FOR_PARTITION)
                    .errorMessage("Not leader for partition")
                    .build();
        }
        
        // Get or create WAL for partition
        WriteAheadLog wal = partitionLogs.computeIfAbsent(
                topicPartition,
                k -> new WriteAheadLog(request.getTopic(), request.getPartition(), config)
        );
        
        try {
            // Clean ACK logic based on required acks
            Integer acks = request.getRequiredAcks();
            if (acks == null) {
                acks = StorageConfig.ACKS_LEADER; // Default to acks=1
            }
            
            if (acks == StorageConfig.ACKS_NONE) {
                return handleAcksNone(request, wal);
            } else if (acks == StorageConfig.ACKS_LEADER) {
                return handleAcksLeader(request, wal);
            } else if (acks == StorageConfig.ACKS_ALL) {
                return handleAcksAll(request, wal);
            } else {
                return ProduceResponse.builder()
                        .topic(request.getTopic())
                        .partition(request.getPartition())
                        .success(false)
                        .errorCode(ProduceResponse.ErrorCode.INVALID_REQUEST)
                        .errorMessage("Invalid acks value: " + acks)
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Error appending messages to {}", topicPartition, e);
            
            return ProduceResponse.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .success(false)
                    .errorCode(ProduceResponse.ErrorCode.INVALID_REQUEST)
                    .errorMessage("Failed to append messages: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public ConsumeResponse fetch(ConsumeRequest request) {
        String topicPartition = getTopicPartitionKey(request.getTopic(), request.getPartition());
        
        log.debug("Fetching messages from {} starting at offset {}", topicPartition, request.getOffset());
        
        WriteAheadLog wal = partitionLogs.get(topicPartition);
        
        if (wal == null) {
            return ConsumeResponse.builder()
                    .success(false)
                    .errorMessage("Partition not found")
                    .messages(new ArrayList<>())
                    .build();
        }
        
        try {
            // Get max messages to fetch (use default if not specified)
            int maxMessages = request.getMaxMessages() != null ? 
                request.getMaxMessages() : config.getConsumer().getDefaultMaxMessages();
            
            // Get max wait time (use default if not specified)
            long maxWaitMs = request.getMaxWaitMs() != null ? 
                request.getMaxWaitMs() : StorageConfig.FETCH_MAX_WAIT_DEFAULT;
            
            // Try to fetch messages immediately
            List<Message> messages = wal.read(request.getOffset(), maxMessages);
            
            // If we have messages or maxWaitMs is 0, return immediately
            if (!messages.isEmpty() || maxWaitMs <= 0) {
                return createConsumeResponse(messages, wal.getHighWaterMark(), request);
            }
            
            // Implement long polling: wait for new messages up to maxWaitMs
            long startTime = System.currentTimeMillis();
            long remainingWaitMs = maxWaitMs;
            
            while (remainingWaitMs > 0) {
                // Wait for a short interval before checking again
                long pollIntervalMs = Math.min(100, remainingWaitMs); // Poll every 100ms or remaining time
                
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Fetch interrupted for {}", topicPartition);
                    break;
                }
                
                // Check for new messages
                messages = wal.read(request.getOffset(), maxMessages);
                if (!messages.isEmpty()) {
                    log.debug("Found {} messages after waiting {}ms for {}", 
                             messages.size(), System.currentTimeMillis() - startTime, topicPartition);
                    break;
                }
                
                remainingWaitMs = maxWaitMs - (System.currentTimeMillis() - startTime);
            }
            
            return createConsumeResponse(messages, wal.getHighWaterMark(), request);
            
        } catch (Exception e) {
            log.error("Error fetching messages", e);
            
            return ConsumeResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .messages(new ArrayList<>())
                    .build();
        }
    }
    
    /**
     * Create a standardized consume response
     */
    private ConsumeResponse createConsumeResponse(List<Message> messages, long highWaterMark, ConsumeRequest request) {
        // Set topic and partition on messages
        messages.forEach(message -> {
            message.setTopic(request.getTopic());
            message.setPartition(request.getPartition());
        });
        
        return ConsumeResponse.builder()
                .messages(messages)
                .highWaterMark(highWaterMark)
                .success(true)
                .build();
    }

    @Override
    public Long getHighWaterMark(String topic, Integer partition) {
        String topicPartition = getTopicPartitionKey(topic, partition);
        WriteAheadLog wal = partitionLogs.get(topicPartition);
        
        return wal != null ? wal.getHighWaterMark() : 0L;
    }

    @Override
    public void flush() {
        log.debug("Flushing all partition logs");
        
        partitionLogs.values().forEach(WriteAheadLog::flush);
    }

    @Override
    public boolean isLeaderForPartition(String topic, Integer partition) {
        // Use metadata store to check leadership
        return metadataStore.isLeaderForPartition(topic, partition);
    }

    @Override
    public Long getLogEndOffset(String topic, Integer partition) {
        String topicPartition = getTopicPartitionKey(topic, partition);
        WriteAheadLog wal = partitionLogs.get(topicPartition);
        
        return wal != null ? wal.getLogEndOffset() : 0L;
    }

    @Override
    public ProduceResponse replicateMessages(ProduceRequest request) {
        String topicPartition = getTopicPartitionKey(request.getTopic(), request.getPartition());

        log.info("Replicating {} messages to {} (follower)", 
                request.getMessages().size(), topicPartition);
        
        // Step 1: Validate request (skip leadership check for replication)
        ProduceResponse.ErrorCode validationError = validateProduceRequest(request);
        if (validationError != ProduceResponse.ErrorCode.NONE) {
            return ProduceResponse.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .success(false)
                    .errorCode(validationError)
                    .errorMessage(validationError.getMessage())
                    .build();
        }
        
        // Get or create WAL for partition
        WriteAheadLog wal = partitionLogs.computeIfAbsent(
                topicPartition,
                k -> new WriteAheadLog(request.getTopic(), request.getPartition(), config)
        );
        
        try {
            // For replication, always use acks=0 (no further replication needed)
            List<ProduceResponse.ProduceResult> results = new ArrayList<>();
            long baseOffset = -1;
            
            synchronized (wal) {
                for (ProduceRequest.ProduceMessage produceMessage : request.getMessages()) {
                    Message message = Message.builder()
                            .key(produceMessage.getKey())
                            .value(produceMessage.getValue())
                            .topic(request.getTopic())
                            .partition(request.getPartition())
                            .timestamp(produceMessage.getTimestamp() != null ? 
                                     produceMessage.getTimestamp() : System.currentTimeMillis())
                            .build();
                    
                    long offset = wal.append(message);
                    message.setOffset(offset);
                    
                    if (baseOffset == -1) {
                        baseOffset = offset;
                    }
                    
                    results.add(ProduceResponse.ProduceResult.builder()
                            .offset(offset)
                            .timestamp(message.getTimestamp())
                            .errorCode(ProduceResponse.ErrorCode.NONE)
                            .build());
                    
                    log.debug("Replicated message at offset: {}", offset);
                }
            }
            
            // For followers, we don't update HWM or replicate further
            // HWM is managed by the leader
            
            return ProduceResponse.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .results(results)
                    .success(true)
                    .errorCode(ProduceResponse.ErrorCode.NONE)
                    .build();
            
        } catch (Exception e) {
            log.error("Error replicating messages to {}", topicPartition, e);
            
            return ProduceResponse.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .success(false)
                    .errorCode(ProduceResponse.ErrorCode.INVALID_REQUEST)
                    .errorMessage("Failed to replicate messages: " + e.getMessage())
                    .build();
        }
    }

    private String getTopicPartitionKey(String topic, Integer partition) {
        return topic + "-" + partition;
    }
    
    /**
     * Validate produce request parameters
     */
    private ProduceResponse.ErrorCode validateProduceRequest(ProduceRequest request) {
        if (request.getTopic() == null || request.getTopic().isEmpty()) {
            return ProduceResponse.ErrorCode.INVALID_REQUEST;
        }
        if (request.getPartition() == null) {
            return ProduceResponse.ErrorCode.INVALID_REQUEST;
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return ProduceResponse.ErrorCode.INVALID_REQUEST;
        }
        return ProduceResponse.ErrorCode.NONE;
    }
    
    /**
     * Result of replication operation with ISR details
     */
    private static class ReplicationResult {
        private final boolean successful;
        private final int successfulReplicas;
        private final int totalReplicas;
        
        public ReplicationResult(boolean successful, int successfulReplicas, int totalReplicas) {
            this.successful = successful;
            this.successfulReplicas = successfulReplicas;
            this.totalReplicas = totalReplicas;
        }
        
        public boolean isSuccessful() { return successful; }
        public int getSuccessfulReplicas() { return successfulReplicas; }
        public int getTotalReplicas() { return totalReplicas; }
    }
    
    /**
     * Replicate to ISR replicas and return detailed replication result
     */
    private ReplicationResult replicateToISR(ProduceRequest request, long baseOffset) {
        // For acks=-1, we need all ISR replicas to acknowledge
        boolean replicationSuccess = replicationManager.replicateBatch(
                request.getTopic(),
                request.getPartition(),
                request.getMessages(),
                baseOffset,
                StorageConfig.ACKS_ALL
        );
        
        // Get ISR information for detailed result
        // Note: This is a simplified implementation. In a real Kafka implementation,
        // the ReplicationManager would return more detailed ISR information.
        // For now, we assume replication success means all ISR replicas acknowledged.
        int totalISR = getISRCount(request.getTopic(), request.getPartition());
        int successfulReplicas = replicationSuccess ? totalISR : 0;
        
        return new ReplicationResult(replicationSuccess, successfulReplicas, totalISR);
    }
    
    /**
     * Get the number of ISR replicas for a partition
     * This is a placeholder - in real implementation, this would come from MetadataStore
     */
    private int getISRCount(String topic, Integer partition) {
        // TODO: Implement proper ISR count from MetadataStore
        // For now, assume at least 1 (the leader)
        return 1;
    }
    
    /**
     * Handle acks=0: No acknowledgment required, just append to leader
     */
    private ProduceResponse handleAcksNone(ProduceRequest request, WriteAheadLog wal) {
        List<ProduceResponse.ProduceResult> results = new ArrayList<>();
        long baseOffset = -1;
        
        synchronized (wal) {
            for (ProduceRequest.ProduceMessage produceMessage : request.getMessages()) {
                Message message = Message.builder()
                        .key(produceMessage.getKey())
                        .value(produceMessage.getValue())
                        .topic(request.getTopic())
                        .partition(request.getPartition())
                        .timestamp(produceMessage.getTimestamp() != null ? 
                                 produceMessage.getTimestamp() : System.currentTimeMillis())
                        .build();
                
                long offset = wal.append(message);
                message.setOffset(offset);
                
                if (baseOffset == -1) {
                    baseOffset = offset;
                }
                
                results.add(ProduceResponse.ProduceResult.builder()
                        .offset(offset)
                        .timestamp(message.getTimestamp())
                        .errorCode(ProduceResponse.ErrorCode.NONE)
                        .build());
                
                log.debug("Message appended at offset: {} (acks=0)", offset);
            }
        }
        
        return ProduceResponse.builder()
                .topic(request.getTopic())
                .partition(request.getPartition())
                .results(results)
                .success(true)
                .errorCode(ProduceResponse.ErrorCode.NONE)
                .build();
    }
    
    /**
     * Handle acks=1: Wait for leader acknowledgment only
     */
    private ProduceResponse handleAcksLeader(ProduceRequest request, WriteAheadLog wal) {
        List<ProduceResponse.ProduceResult> results = new ArrayList<>();
        long baseOffset = -1;
        
        synchronized (wal) {
            for (ProduceRequest.ProduceMessage produceMessage : request.getMessages()) {
                Message message = Message.builder()
                        .key(produceMessage.getKey())
                        .value(produceMessage.getValue())
                        .topic(request.getTopic())
                        .partition(request.getPartition())
                        .timestamp(produceMessage.getTimestamp() != null ? 
                                 produceMessage.getTimestamp() : System.currentTimeMillis())
                        .build();
                
                long offset = wal.append(message);
                message.setOffset(offset);
                
                if (baseOffset == -1) {
                    baseOffset = offset;
                }
                
                results.add(ProduceResponse.ProduceResult.builder()
                        .offset(offset)
                        .timestamp(message.getTimestamp())
                        .errorCode(ProduceResponse.ErrorCode.NONE)
                        .build());
                
                log.debug("Message appended at offset: {} (acks=1)", offset);
            }
        }
        
        // For acks=1, we acknowledge immediately after leader append
        // No replication required
        return ProduceResponse.builder()
                .topic(request.getTopic())
                .partition(request.getPartition())
                .results(results)
                .success(true)
                .errorCode(ProduceResponse.ErrorCode.NONE)
                .build();
    }
    
    /**
     * Handle acks=-1: Wait for all ISR replicas to acknowledge
     */
    private ProduceResponse handleAcksAll(ProduceRequest request, WriteAheadLog wal) {
        List<ProduceResponse.ProduceResult> results = new ArrayList<>();
        long baseOffset = -1;
        
        synchronized (wal) {
            for (ProduceRequest.ProduceMessage produceMessage : request.getMessages()) {
                Message message = Message.builder()
                        .key(produceMessage.getKey())
                        .value(produceMessage.getValue())
                        .topic(request.getTopic())
                        .partition(request.getPartition())
                        .timestamp(produceMessage.getTimestamp() != null ? 
                                 produceMessage.getTimestamp() : System.currentTimeMillis())
                        .build();
                
                long offset = wal.append(message);
                message.setOffset(offset);
                
                if (baseOffset == -1) {
                    baseOffset = offset;
                }
                
                results.add(ProduceResponse.ProduceResult.builder()
                        .offset(offset)
                        .timestamp(message.getTimestamp())
                        .errorCode(ProduceResponse.ErrorCode.NONE)
                        .build());
                
                log.debug("Message appended at offset: {} (acks=-1)", offset);
            }
        }
        
        // Step 2: Replicate to ISR replicas and check min ISR requirement
        ReplicationResult replicationResult = replicateToISR(request, baseOffset);
        
        if (!replicationResult.isSuccessful()) {
            log.error("Replication failed for topic-partition: {}, cannot acknowledge producer", 
                     getTopicPartitionKey(request.getTopic(), request.getPartition()));
            return ProduceResponse.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .success(false)
                    .errorCode(ProduceResponse.ErrorCode.INVALID_REQUEST)
                    .errorMessage("Replication to ISR replicas failed")
                    .build();
        }
        
        // Step 3: Update High Watermark only if we have enough ISR replicas
        if (replicationResult.getSuccessfulReplicas() >= config.getReplication().getMinInsyncReplicas()) {
            long currentLeo = wal.getLogEndOffset();
            wal.updateHighWaterMark(currentLeo);
            log.debug("Updated High Watermark to {} for topic-partition: {} (ISR replicas: {}/{})", 
                     currentLeo, getTopicPartitionKey(request.getTopic(), request.getPartition()),
                     replicationResult.getSuccessfulReplicas(), replicationResult.getTotalReplicas());
        } else {
            log.warn("Not enough ISR replicas for HWM advancement: {}/{} < minISR={}", 
                    replicationResult.getSuccessfulReplicas(), replicationResult.getTotalReplicas(),
                    config.getReplication().getMinInsyncReplicas());
        }
        
        return ProduceResponse.builder()
                .topic(request.getTopic())
                .partition(request.getPartition())
                .results(results)
                .success(true)
                .errorCode(ProduceResponse.ErrorCode.NONE)
                .build();
    }
}
