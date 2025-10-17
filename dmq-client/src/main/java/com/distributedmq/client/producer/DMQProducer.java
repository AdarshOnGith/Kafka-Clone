package com.distributedmq.client.producer;

import com.distributedmq.client.producer.contracts.Producer;
import com.distributedmq.client.producer.client.MetadataServiceClient;
import com.distributedmq.client.producer.client.StorageServiceClient;
import com.distributedmq.common.dto.ProduceResponse;
import com.distributedmq.common.model.BrokerNode;
import com.distributedmq.common.model.PartitionMetadata;
import com.distributedmq.common.model.TopicMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Default implementation of Producer
 * Phase 2: Metadata fetch + partition selection + leader connection
 */
@Slf4j
public class DMQProducer implements Producer {

    private final ProducerConfig config;
    private final MetadataServiceClient metadataClient;
    private final PartitionSelector partitionSelector;
    private final StorageServiceClient storageClient;
    private volatile boolean closed = false;

    public DMQProducer(ProducerConfig config) {
        this.config = config;
        this.metadataClient = new MetadataServiceClient(config.getMetadataServiceUrl());
        this.partitionSelector = new PartitionSelector();
        this.storageClient = new StorageServiceClient();

        log.info("DMQProducer initialized with metadata service: {}", config.getMetadataServiceUrl());
    }

    @Override
    public Future<ProduceResponse> send(String topic, String key, byte[] value) {
        return send(topic, null, key, value);
    }

    @Override
    public Future<ProduceResponse> send(String topic, Integer partition, String key, byte[] value) {
        validateNotClosed();

        log.info("========================================");
        log.info("PRODUCER: Sending message to topic '{}'", topic);
        log.info("Key: {}, Value size: {} bytes", key, value != null ? value.length : 0);

        try {
            // Phase 1: Fetch metadata from metadata service
            log.info("STEP 1: Fetching metadata for topic '{}'...", topic);
            TopicMetadata metadata = metadataClient.getTopicMetadata(topic);

            log.info("✅ Successfully fetched metadata:");
            log.info("  - Topic: {}", metadata.getTopicName());
            log.info("  - Partition Count: {}", metadata.getPartitionCount());
            log.info("  - Partitions: {}", metadata.getPartitions().size());

            // Phase 2: Select partition and connect to leader
            log.info("STEP 2: Selecting partition and connecting to leader...");

            // Determine partition (use provided partition or select based on key)
            PartitionMetadata targetPartition;
            if (partition != null) {
                // Specific partition requested
                targetPartition = metadata.getPartitions().stream()
                        .filter(p -> p.getPartitionId().equals(partition))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Partition " + partition + " not found"));
                log.info("Using specified partition: {}", partition);
            } else {
                // Auto-select partition based on key
                targetPartition = partitionSelector.selectPartition(metadata, key);
            }

            // Get leader broker for the selected partition
            BrokerNode leaderBroker = partitionSelector.getLeaderBroker(targetPartition);

            // Phase 2: Connect to leader broker
            log.info("STEP 3: Connecting to leader broker...");
            String leaderUrl = leaderBroker.getAddress();
            // Add http:// prefix only if not already present
            if (!leaderUrl.startsWith("http://") && !leaderUrl.startsWith("https://")) {
                leaderUrl = "http://" + leaderUrl;
            }
            boolean connected = storageClient.connectToBroker(leaderUrl);

            if (!connected) {
                throw new RuntimeException("Failed to connect to leader broker: " + leaderUrl);
            }

            log.info("✅ Successfully connected to leader broker: {}", leaderUrl);

            // Phase 3: Send message to storage service
            log.info("STEP 4: Sending message to storage service...");
            ProduceResponse storageResponse = storageClient.sendMessage(
                    leaderUrl, topic, targetPartition.getPartitionId(), key, value);

            log.info("🎉 Phase 3 Complete: Message sent to storage service!");
            log.info("   Response: {}", storageResponse);
            log.info("========================================");

            // Return the actual response from storage service
            CompletableFuture<ProduceResponse> future = new CompletableFuture<>();
            future.complete(storageResponse);
            return future;

        } catch (Exception e) {
            log.error("❌ Failed to send message", e);
            CompletableFuture<ProduceResponse> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    @Override
    public ProduceResponse sendSync(String topic, String key, byte[] value) {
        try {
            return send(topic, key, value).get();
        } catch (Exception e) {
            log.error("Error sending message synchronously", e);
            return ProduceResponse.builder()
                    .topic(topic)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public void flush() {
        log.debug("Flushing producer (no-op in Phase 1)");
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            log.info("DMQProducer closed");
        }
    }

    private void validateNotClosed() {
        if (closed) {
            throw new IllegalStateException("Producer is closed");
        }
    }
}
