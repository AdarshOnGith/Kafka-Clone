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
        
        // Get or create WAL for partition
        //topicPartition is k (key), if absent, create new WAL with key k = topicPartition
        WriteAheadLog wal = partitionLogs.computeIfAbsent(
                topicPartition,
                k -> new WriteAheadLog(request.getTopic(), request.getPartition(), config)
        );
        
        try {
            List<ProduceResponse.ProduceResult> results = new ArrayList<>();
            long baseOffset = -1;
            
            // Step 1: Append Messages to Partition Log
            // Synchronizes on WAL(monitor lock on wal) to prevent race conditions and ensure thread-safe message writes
            synchronized (wal) {
                for (ProduceRequest.ProduceMessage produceMessage : request.getMessages()) {
                    // Create message
                    Message message = Message.builder()
                            .key(produceMessage.getKey())
                            .value(produceMessage.getValue())
                            .topic(request.getTopic())
                            .partition(request.getPartition())
                            .timestamp(produceMessage.getTimestamp() != null ? 
                                     produceMessage.getTimestamp() : System.currentTimeMillis())
                            .build();
                    
                    // Append to WAL and assign offset
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
                    
                    log.debug("Message appended at offset: {}", offset);
                }
            }
            
            // Step 2: Replicate to Followers
            if (request.getRequiredAcks() != null && request.getRequiredAcks() != 0) {
                boolean replicationSuccess = replicationManager.replicateBatch(
                        request.getTopic(),
                        request.getPartition(),
                        request.getMessages(),
                        baseOffset,
                        request.getRequiredAcks()
                );

                if (!replicationSuccess) {
                    // TODO: Handle replication failure based on acks policy
                    // For now, assume acks=-1 (wait for all followers to commit)
                    // If replication fails, return error to producer
                    log.error("Replication failed for topic-partition: {}, cannot acknowledge producer", topicPartition);
                    return ProduceResponse.builder()
                            .topic(request.getTopic())
                            .partition(request.getPartition())
                            .success(false)
                            .errorCode(ProduceResponse.ErrorCode.INVALID_REQUEST)
                            .errorMessage("Replication to followers failed")
                            .build();
                }
            }
            
            // Step 3: Update Offsets
            // Log End Offset (LEO) is already updated in WAL.append()
            // High Watermark (HW) needs to be updated after successful replication
            if (request.getRequiredAcks() != null && request.getRequiredAcks() != 0) {
                // After successful replication, update High Watermark to LEO
                long currentLeo = wal.getLogEndOffset();
                wal.updateHighWaterMark(currentLeo);
                log.debug("Updated High Watermark to {} for topic-partition: {}", currentLeo, topicPartition);
            }
            
            // Step 4: Send Acknowledgment to Producer
            return ProduceResponse.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .results(results)
                    .success(true)
                    .errorCode(ProduceResponse.ErrorCode.NONE)
                    .build();
            
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
            List<Message> messages = wal.read(
                    request.getOffset(),
                    request.getMaxMessages() != null ? request.getMaxMessages() : config.getConsumer().getDefaultMaxMessages()
            );
            
            return ConsumeResponse.builder()
                    .messages(messages)
                    .highWaterMark(wal.getHighWaterMark())
                    .success(true)
                    .build();
            
        } catch (Exception e) {
            log.error("Error fetching messages", e);
            
            return ConsumeResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .messages(new ArrayList<>())
                    .build();
        }
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

    private String getTopicPartitionKey(String topic, Integer partition) {
        return topic + "-" + partition;
    }
}
