package com.distributedmq.storage.service;

import com.distributedmq.common.dto.ConsumeRequest;
import com.distributedmq.common.dto.ConsumeResponse;
import com.distributedmq.common.dto.ProduceRequest;
import com.distributedmq.common.dto.ProduceResponse;
import com.distributedmq.common.model.Message;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    // Map of topic-partition to WAL
    private final Map<String, WriteAheadLog> partitionLogs = new ConcurrentHashMap<>();
    
    private final ReplicationManager replicationManager;

    @Override
    public ProduceResponse append(ProduceRequest request) {
        String topicPartition = getTopicPartitionKey(request.getTopic(), request.getPartition());
        
        log.debug("Appending message to {}", topicPartition);
        
        // Get or create WAL for partition
        WriteAheadLog wal = partitionLogs.computeIfAbsent(
                topicPartition,
                k -> new WriteAheadLog(request.getTopic(), request.getPartition())
        );
        
        try {
            // Create message
            Message message = Message.builder()
                    .key(request.getKey())
                    .value(request.getValue())
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            // Append to WAL
            long offset = wal.append(message);
            message.setOffset(offset);
            
            // Replicate to followers
            if (request.getRequiredAcks() != 0) {
                replicationManager.replicate(message);
            }
            
            log.debug("Message appended at offset: {}", offset);
            
            return ProduceResponse.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .offset(offset)
                    .timestamp(message.getTimestamp())
                    .success(true)
                    .build();
            
        } catch (Exception e) {
            log.error("Error appending message", e);
            
            return ProduceResponse.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .success(false)
                    .errorMessage(e.getMessage())
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
                    request.getMaxMessages() != null ? request.getMaxMessages() : 100
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

    private String getTopicPartitionKey(String topic, Integer partition) {
        return topic + "-" + partition;
    }
}
