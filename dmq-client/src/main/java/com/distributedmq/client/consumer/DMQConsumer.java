package com.distributedmq.client.consumer;

import com.distributedmq.common.dto.BrokerInfo;
import com.distributedmq.common.dto.ConsumerSubscriptionRequest;
import com.distributedmq.common.dto.ConsumerSubscriptionResponse;
import com.distributedmq.common.dto.ConsumeRequest;
import com.distributedmq.common.dto.ConsumeResponse;
import com.distributedmq.common.dto.PartitionInfo;
import com.distributedmq.common.model.BrokerNode;
import com.distributedmq.common.model.ConsumerOffset;
import com.distributedmq.common.model.Message;
import com.distributedmq.common.model.PartitionMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default implementation of Consumer
 * Phase 1: Single consumer per group, simple polling
 * Phase 2+: Multi-member groups with client-side rebalancing
 */
@Slf4j
public class DMQConsumer implements Consumer {

    private final ConsumerConfig config;
    private final ConsumerEgressClient egressClient;
    
    private volatile boolean closed = false;
    private volatile boolean subscribed = false;
    
    // Consumer group membership
    private String consumerId;
    private Integer generationId;  // For future rebalancing
    
    // Partition metadata with leader, offsets, ISR
    private final Map<TopicPartition, PartitionMetadata> partitionMetadata = new ConcurrentHashMap<>();
    
    // Current fetch positions (offset to fetch next)
    private final Map<TopicPartition, Long> fetchPositions = new ConcurrentHashMap<>();
    
    // Committed offsets (for future use)
    private final Map<TopicPartition, Long> committedOffsets = new ConcurrentHashMap<>();
    
    // Heartbeat thread (for future multi-member groups)
    private Thread heartbeatThread;

    public DMQConsumer(ConsumerConfig config) {
        this.config = config;
        
        // Generate unique consumer ID
        this.consumerId = config.getClientId() + "-" + UUID.randomUUID().toString().substring(0, 8);  // unique id for all consumers
        
        // Initialize egress client
        this.egressClient = new ConsumerEgressClient(config.getMetadataServiceUrl()); //network layer implementation, communicate over network with CSE,storage nodes
        
        log.info("DMQConsumer initialized: consumerId={}, groupId={}", consumerId, config.getGroupId());
    }


    @Override
    public void subscribe(String topic) {
        validateNotClosed(); // Ensure consumer is not closed
        
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic cannot be null or empty");
        }
        
        log.info("Subscribing to topic: {} with group: {}", topic, config.getGroupId());
        
        // Build join group request
        ConsumerSubscriptionRequest request = ConsumerSubscriptionRequest.builder()
            .groupId(config.getGroupId())
            .consumerId(consumerId)
            .topic(topic)  // Single topic only
            .clientId(config.getClientId())
            .build();
        
        // Send request to Consumer Egress Service
        // CES will create/join group and return broker + partition metadata
        ConsumerSubscriptionResponse response = egressClient.joinGroup(request);
        
        if (!response.isSuccess()) {
            String errorMsg = "Failed to join consumer group: " + response.getErrorMessage();
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        
        // Update consumer state
        this.generationId = response.getGenerationId();  // May be null in Phase 1
        this.subscribed = true;
        
        // Clear existing state
        partitionMetadata.clear();
        fetchPositions.clear();
        
        // Step 1: Build broker map from response
        Map<Integer, BrokerNode> brokerMap = new HashMap<>();
        if (response.getBrokers() != null) {
            for (BrokerInfo brokerInfo : response.getBrokers()) {
                BrokerNode broker = new BrokerNode(
                    brokerInfo.getId(),
                    brokerInfo.getHost(),
                    brokerInfo.getPort()
                );
                brokerMap.put(brokerInfo.getId(), broker);
                log.debug("Registered broker: {}", broker);
            }
        }
        
        // Step 2: Transform PartitionInfo to PartitionMetadata with BrokerNode objects
        if (response.getPartitions() != null) {
            for (PartitionInfo partitionInfo : response.getPartitions()) {
                // Get leader broker
                BrokerNode leader = brokerMap.get(partitionInfo.getLeaderId());
                if (leader == null) {
                    log.error("Leader broker ID {} not found in broker map for partition {}-{}", 
                        partitionInfo.getLeaderId(), partitionInfo.getTopic(), partitionInfo.getPartition());
                    throw new RuntimeException("Leader broker not found: " + partitionInfo.getLeaderId());
                }
                
                // Build ISR list
                List<BrokerNode> isr = partitionInfo.getIsrIds().stream()
                    .map(brokerMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                
                // Build replicas list
                List<BrokerNode> replicas = partitionInfo.getFollowerIds().stream()
                    .map(brokerMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                
                // Create PartitionMetadata with BrokerNode objects
                PartitionMetadata metadata = PartitionMetadata.builder()
                    .topicName(partitionInfo.getTopic())
                    .partitionId(partitionInfo.getPartition())
                    .leader(leader)
                    .replicas(replicas)
                    .isr(isr)
                    .build();
                
                TopicPartition tp = new TopicPartition(metadata.getTopic(), metadata.getPartition());
                
                // Store partition metadata
                partitionMetadata.put(tp, metadata);
                
                // Initialize fetch position to 0 (local offset management)
                fetchPositions.put(tp, 0L);
                committedOffsets.put(tp, 0L);
                
                log.info("Assigned partition {}-{} with leader {} (starting at offset 0)", 
                    metadata.getTopicName(), metadata.getPartitionId(), leader.getAddress());
                
                log.debug("Partition metadata: topic={}, partition={}, leader={}, replicas={}, isr={}",
                    metadata.getTopicName(), metadata.getPartitionId(), 
                    leader.getAddress(),
                    replicas.size(), isr.size());
            }
        }
        
        log.info("Successfully subscribed to topic {} with {} partitions", 
            topic, partitionMetadata.size());
    }
    
    /**
     * Subscribe to multiple topics (DEPRECATED - Use single topic subscribe)
     * Kept for backward compatibility but not recommended
     */
    @Deprecated
    public void subscribe(Collection<String> topics) {
        if (topics == null || topics.isEmpty()) {
            throw new IllegalArgumentException("Topics cannot be null or empty");
        }
        if (topics.size() > 1) {
            throw new UnsupportedOperationException("Multiple topic subscription not supported. Subscribe to one topic at a time.");
        }
        // Delegate to single topic subscribe
        subscribe(topics.iterator().next());
    }

    @Override
    public void assign(Collection<TopicPartition> partitions) {
        validateNotClosed();
        
        if (partitions == null || partitions.isEmpty()) {
            throw new IllegalArgumentException("Partitions cannot be null or empty");
        }
        
        log.info("Manual partition assignment: {}", partitions);
        
        // TODO Phase 2: Implement manual partition assignment (without consumer group)
        // This is for advanced use cases where user wants to:
        // 1. Manually specify which partitions to consume
        // 2. NOT use consumer groups
        // 3. Manage offsets manually
        //
        // Implementation steps:
        // 1. Query metadata service for each partition's leader
        // 2. Initialize fetch positions (no committed offsets, start from 0 or config)
        // 3. Store partition metadata
        // 4. Mark as subscribed
        //
        // Note: No consumer group coordination needed!
        
        log.warn("Manual assignment not yet implemented. Partitions: {}", partitions);
        throw new UnsupportedOperationException("Manual partition assignment not yet implemented in Phase 1");
    }


    @Override
    public List<Message> poll(long timeoutMs) {
        validateNotClosed();
        
        if (!subscribed || partitionMetadata.isEmpty()) {
            log.debug("No partitions assigned yet, returning empty list");
            return new ArrayList<>();
        }
        
        log.debug("Polling for messages with timeout: {}ms from {} partitions", 
            timeoutMs, partitionMetadata.size());
        
        List<Message> allMessages = new ArrayList<>();
        
        // Fetch from each assigned partition
        for (Map.Entry<TopicPartition, PartitionMetadata> entry : partitionMetadata.entrySet()) {
            TopicPartition tp = entry.getKey();
            PartitionMetadata metadata = entry.getValue();
            
            // Get current fetch position
            Long fetchOffset = fetchPositions.get(tp);
            if (fetchOffset == null) {
                log.warn("No fetch position for partition {}, skipping", tp);
                continue;
            }
            
            // Build fetch request
            ConsumeRequest request = ConsumeRequest.builder()
                .consumerGroup(config.getGroupId())
                .topic(tp.getTopic())
                .partition(tp.getPartition())
                .offset(fetchOffset)
                .maxMessages(config.getMaxPollRecords())
                .maxWaitMs(timeoutMs)
                .minBytes(config.getFetchMinBytes())
                .maxBytes(config.getFetchMaxBytes())
                .build();
            
            // Get leader broker URL
            String leaderUrl = "http://" + metadata.getLeader().getAddress();
            
            // Fetch messages from storage service
            ConsumeResponse response = egressClient.fetchMessages(request, leaderUrl);
            
            if (response.isSuccess() && response.getMessages() != null) {
                List<Message> messages = response.getMessages();
                allMessages.addAll(messages);
                
                // Update fetch position to next offset after last message
                if (!messages.isEmpty()) {
                    Message lastMessage = messages.get(messages.size() - 1);
                    Long nextOffset = lastMessage.getOffset() + 1;
                    fetchPositions.put(tp, nextOffset);
                    
                    log.debug("Fetched {} messages from {}, next offset: {}", 
                        messages.size(), tp, nextOffset);
                }
            } else {
                log.debug("No messages fetched from {}: {}", tp, 
                    response.getErrorMessage() != null ? response.getErrorMessage() : "empty");
            }
        }
        
        // TODO Phase 2: Auto-commit if enabled
        if (config.getEnableAutoCommit() && !allMessages.isEmpty()) {
            log.trace("Auto-commit enabled but not yet implemented in Phase 1");
        }
        
        log.debug("Poll returned {} total messages", allMessages.size());
        return allMessages;
    }

    @Override
    public void commitSync() {
        validateNotClosed();
        
        if (!subscribed) {
            log.warn("Cannot commit: consumer not subscribed");
            return;
        }
        
        // TODO Phase 2: Implement offset commit
        log.debug("Offset commit not yet implemented in Phase 1");
        
        /* Future implementation:
        List<ConsumerOffset> offsets = new ArrayList<>();
        for (Map.Entry<TopicPartition, Long> entry : fetchPositions.entrySet()) {
            TopicPartition tp = entry.getKey();
            Long offset = entry.getValue();
            
            ConsumerOffset consumerOffset = ConsumerOffset.builder()
                .consumerGroup(config.getGroupId())
                .topic(tp.getTopic())
                .partition(tp.getPartition())
                .offset(offset)
                .timestamp(System.currentTimeMillis())
                .build();
            
            offsets.add(consumerOffset);
        }
        
        boolean success = egressClient.commitOffsets(config.getGroupId(), offsets);
        if (success) {
            offsets.forEach(offset -> {
                TopicPartition tp = new TopicPartition(offset.getTopic(), offset.getPartition());
                committedOffsets.put(tp, offset.getOffset());
            });
            log.info("Successfully committed offsets for {} partitions", offsets.size());
        } else {
            throw new RuntimeException("Failed to commit offsets");
        }
        */
    }

    @Override
    public void commitAsync() {
        validateNotClosed();
        
        // TODO Phase 2: Implement async commit
        log.debug("Async commit not yet implemented in Phase 1");
    }

    @Override
    public void seek(TopicPartition partition, long offset) {
        validateNotClosed();
        
        if (!partitionMetadata.containsKey(partition)) {
            throw new IllegalArgumentException("Partition " + partition + " is not assigned to this consumer");
        }
        
        fetchPositions.put(partition, offset);
        log.info("Seek to offset {} for partition {}", offset, partition);
    }

    @Override
    public void seekToBeginning(Collection<TopicPartition> partitions) {
        validateNotClosed();
        
        for (TopicPartition tp : partitions) {
            PartitionMetadata metadata = partitionMetadata.get(tp);
            if (metadata != null) {
                Long startOffset = metadata.getStartOffset() != null ? metadata.getStartOffset() : 0L;
                fetchPositions.put(tp, startOffset);
                log.info("Seek to beginning (offset {}) for partition {}", startOffset, tp);
            } else {
                log.warn("Partition {} is not assigned, cannot seek", tp);
            }
        }
    }

    @Override
    public void seekToEnd(Collection<TopicPartition> partitions) {
        validateNotClosed();
        
        for (TopicPartition tp : partitions) {
            PartitionMetadata metadata = partitionMetadata.get(tp);
            if (metadata != null) {
                // Use highWaterMark as the end offset
                Long endOffset = metadata.getHighWaterMark() != null ? metadata.getHighWaterMark() : 0L;
                fetchPositions.put(tp, endOffset);
                log.info("Seek to end (offset {}) for partition {}", endOffset, tp);
            } else {
                log.warn("Partition {} is not assigned, cannot seek", tp);
            }
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            
            log.info("Closing DMQConsumer: consumerId={}", consumerId);
            
            // Stop heartbeat thread if running (Phase 2)
            if (heartbeatThread != null && heartbeatThread.isAlive()) {
                heartbeatThread.interrupt();
                try {
                    heartbeatThread.join(1000);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for heartbeat thread to stop");
                    Thread.currentThread().interrupt();
                }
            }
            
            // TODO Phase 2: Leave consumer group
            /*
            if (subscribed && config.getGroupId() != null) {
                try {
                    egressClient.leaveGroup(config.getGroupId(), consumerId);
                    log.info("Left consumer group: {}", config.getGroupId());
                } catch (Exception e) {
                    log.error("Error leaving consumer group: {}", e.getMessage());
                }
            }
            */
            
            // Clear state
            partitionMetadata.clear();
            fetchPositions.clear();
            committedOffsets.clear();
            
            log.info("DMQConsumer closed successfully");
        }
    }

    private void validateNotClosed() {
        if (closed) {
            throw new IllegalStateException("Consumer is closed");
        }
    }
    
    // Getters for testing/debugging
    
    public String getConsumerId() {
        return consumerId;
    }
    
    public Integer getGenerationId() {
        return generationId;
    }
    
    public Map<TopicPartition, PartitionMetadata> getPartitionMetadata() {
        return Collections.unmodifiableMap(partitionMetadata);
    }
    
    public Map<TopicPartition, Long> getFetchPositions() {
        return Collections.unmodifiableMap(fetchPositions);
    }
}
