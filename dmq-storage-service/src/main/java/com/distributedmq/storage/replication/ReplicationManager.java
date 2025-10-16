package com.distributedmq.storage.replication;

import com.distributedmq.common.dto.ProduceRequest;
import com.distributedmq.common.dto.ProduceResponse;
import com.distributedmq.common.dto.ReplicationAck;
import com.distributedmq.common.dto.ReplicationRequest;
import com.distributedmq.common.dto.ReplicationResponse;
import com.distributedmq.storage.config.StorageConfig;
import com.distributedmq.storage.service.StorageService;
import com.distributedmq.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

/**
 * Manages replication of messages to follower brokers
 * Handles network communication and acknowledgment collection
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplicationManager {

    private final RestTemplate restTemplate;
    private final StorageConfig config;
    private final MetadataStore metadataStore;

    /**
     * Replicate a batch of messages to all followers for the partition
     * @return true if replication successful based on requiredAcks, false otherwise
     */
    public boolean replicateBatch(String topic, Integer partition,
                                  List<ProduceRequest.ProduceMessage> messages,
                                  Long baseOffset, Integer requiredAcks) {

        log.info("Starting replication for topic-partition {}-{} with {} messages, baseOffset: {}, requiredAcks: {}",
                topic, partition, messages.size(), baseOffset, requiredAcks);

        // Get followers for this partition
        List<BrokerInfo> followers = metadataStore.getFollowersForPartition(topic, partition);
        if (followers.isEmpty()) {
            log.warn("No followers found for partition {}-{}", topic, partition);
            // If no followers and acks > 0, we need at least the leader ack
            return requiredAcks == StorageConfig.ACKS_NONE; // Only succeed if acks=0 (no replication needed)
        }

        // Create replication request
        ReplicationRequest request = ReplicationRequest.builder()
                .topic(topic)
                .partition(partition)
                .messages(messages)
                .baseOffset(baseOffset)
                .leaderId(config.getBroker().getId())
                .leaderEpoch(metadataStore.getLeaderEpoch(topic, partition))
                .timeoutMs((long) config.getReplication().getFetchMaxWaitMs())
                .requiredAcks(requiredAcks)
                .build();

        // Send replication requests to all followers asynchronously
        List<CompletableFuture<ReplicationAck>> replicationFutures = followers.stream()
                .map(follower -> CompletableFuture.supplyAsync(() ->
                    replicateToFollower(follower, request)))
                .collect(Collectors.toList());

        // Wait for acknowledgments based on requiredAcks
        try {
            long timeoutMs = config.getReplication().getFetchMaxWaitMs();
            List<ReplicationAck> acks = replicationFutures.stream()
                    .map(future -> {
                        try {
                            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
                        } catch (Exception e) {
                            log.error("Failed to get replication result: {}", e.getMessage());
                            // Return a failed ack
                            return ReplicationAck.builder()
                                    .topic(topic)
                                    .partition(partition)
                                    .success(false)
                                    .errorCode(ReplicationAck.ErrorCode.REPLICATION_FAILED)
                                    .errorMessage("Replication timeout or error: " + e.getMessage())
                                    .build();
                        }
                    })
                    .collect(Collectors.toList());

            // Count successful acknowledgments
            long successfulAcks = acks.stream()
                    .filter(ReplicationAck::isSuccess)
                    .count();

            // For acks=1: need at least 1 successful ack (leader already wrote)
            // For acks=-1: need all followers to acknowledge
            boolean replicationSuccessful;
            if (requiredAcks == StorageConfig.ACKS_ALL) {
                replicationSuccessful = successfulAcks == followers.size();
            } else if (requiredAcks == StorageConfig.ACKS_LEADER) {
                replicationSuccessful = successfulAcks >= 1; // At least one follower ack
            } else {
                replicationSuccessful = false; // Invalid acks value
            }

            log.info("Replication completed for {}-{}: {}/{} followers acknowledged successfully",
                    topic, partition, successfulAcks, followers.size());

            return replicationSuccessful;

        } catch (Exception e) {
            log.error("Replication failed for topic-partition {}-{}", topic, partition, e);
            return false;
        }
    }

    /**
     * Send replication request to a specific follower
     */
    private ReplicationAck replicateToFollower(BrokerInfo follower, ReplicationRequest request) {
        String url = String.format("http://%s:%d/api/v1/storage/replicate",
                follower.getHost(), follower.getPort());

        log.debug("Sending replication request to follower {}: {}", follower.getId(), url);

        try {
            HttpEntity<ReplicationRequest> entity = new HttpEntity<>(request);
            ResponseEntity<ReplicationResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, ReplicationResponse.class);

            ReplicationResponse replicationResponse = response.getBody();

            if (replicationResponse != null && replicationResponse.isSuccess()) {
                // Convert to ack
                return ReplicationAck.builder()
                        .topic(request.getTopic())
                        .partition(request.getPartition())
                        .followerId(follower.getId())
                        .baseOffset(request.getBaseOffset())
                        .messageCount(request.getMessages().size())
                        .logEndOffset(replicationResponse.getBaseOffset() + replicationResponse.getMessageCount())
                        .highWaterMark(replicationResponse.getBaseOffset() + replicationResponse.getMessageCount())
                        .success(true)
                        .errorCode(ReplicationAck.ErrorCode.NONE)
                        .build();
            } else {
                log.warn("Replication failed for follower {}: {}",
                        follower.getId(), replicationResponse != null ? replicationResponse.getErrorMessage() : "null response");
                return ReplicationAck.builder()
                        .topic(request.getTopic())
                        .partition(request.getPartition())
                        .followerId(follower.getId())
                        .success(false)
                        .errorCode(ReplicationAck.ErrorCode.REPLICATION_FAILED)
                        .errorMessage(replicationResponse != null ? replicationResponse.getErrorMessage() : "Unknown error")
                        .build();
            }

        } catch (Exception e) {
            log.error("Network error replicating to follower {}: {}", follower.getId(), e.getMessage());
            return ReplicationAck.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .followerId(follower.getId())
                    .success(false)
                    .errorCode(ReplicationAck.ErrorCode.REPLICATION_FAILED)
                    .errorMessage("Network error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Process replication request as a follower
     * This is called when this broker receives a replication request from a leader
     */
    public ReplicationResponse processReplicationRequest(ReplicationRequest request, StorageService storageService) {
        log.info("Processing replication request from leader {} for topic-partition {}-{} with {} messages",
                request.getLeaderId(), request.getTopic(), request.getPartition(), request.getMessages().size());

        // Validate that this broker is a follower for this partition
        if (!metadataStore.isFollowerForPartition(request.getTopic(), request.getPartition())) {
            return ReplicationResponse.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .followerId(config.getBroker().getId())
                    .success(false)
                    .errorCode(ReplicationResponse.ErrorCode.NOT_FOLLOWER_FOR_PARTITION)
                    .errorMessage("This broker is not a follower for partition " + request.getTopic() + "-" + request.getPartition())
                    .build();
        }

        // TODO: Validate leader epoch to prevent stale leader requests

        try {
            // Create a ProduceRequest from the replication request
            ProduceRequest produceRequest = ProduceRequest.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .messages(request.getMessages())
                    .requiredAcks(StorageConfig.FOLLOWER_ACKS) // Followers don't need to replicate further
                    .build();

            // Process the messages through StorageService
            ProduceResponse produceResponse = storageService.appendMessages(produceRequest);

            if (produceResponse.isSuccess()) {
                log.info("Successfully replicated {} messages for topic-partition {}-{}",
                        request.getMessages().size(), request.getTopic(), request.getPartition());

                return ReplicationResponse.builder()
                        .topic(request.getTopic())
                        .partition(request.getPartition())
                        .followerId(config.getBroker().getId())
                        .baseOffset(request.getBaseOffset())
                        .messageCount(request.getMessages().size())
                        .success(true)
                        .errorCode(ReplicationResponse.ErrorCode.NONE)
                        .replicationTimeMs(System.currentTimeMillis())
                        .build();
            } else {
                log.error("Replication failed: {}", produceResponse.getErrorMessage());
                return ReplicationResponse.builder()
                        .topic(request.getTopic())
                        .partition(request.getPartition())
                        .followerId(config.getBroker().getId())
                        .success(false)
                        .errorCode(ReplicationResponse.ErrorCode.STORAGE_ERROR)
                        .errorMessage(produceResponse.getErrorMessage())
                        .build();
            }

        } catch (Exception e) {
            log.error("Error processing replication request: {}", e.getMessage());
            return ReplicationResponse.builder()
                    .topic(request.getTopic())
                    .partition(request.getPartition())
                    .followerId(config.getBroker().getId())
                    .success(false)
                    .errorCode(ReplicationResponse.ErrorCode.STORAGE_ERROR)
                    .errorMessage("Storage error: " + e.getMessage())
                    .build();
        }
    }
}