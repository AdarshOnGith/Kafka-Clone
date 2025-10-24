package com.distributedmq.metadata.coordination;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Metadata State Machine
 * Applies committed Raft log entries to the metadata state
 * 
 * This is where controller decisions (from Raft consensus) 
 * are applied to actual metadata (topics, partitions, brokers)
 */
@Slf4j
@Component
public class MetadataStateMachine {

    // In-memory metadata storage (will be replaced with proper persistence later)
    private final Map<Integer, BrokerInfo> brokers = new ConcurrentHashMap<>();
    private final Map<String, TopicInfo> topics = new ConcurrentHashMap<>();

    /**
     * Apply a committed log entry to the metadata state
     */
    public void apply(Object command) {
        if (command == null) {
            log.warn("Received null command, ignoring");
            return;
        }
        
        log.debug("Applying command: {}", command.getClass().getSimpleName());
        
        if (command instanceof RegisterBrokerCommand) {
            applyRegisterBroker((RegisterBrokerCommand) command);
        } else if (command instanceof UnregisterBrokerCommand) {
            applyUnregisterBroker((UnregisterBrokerCommand) command);
        } else {
            log.warn("Unknown command type: {}", command.getClass().getSimpleName());
        }
    }

    /**
     * Apply broker registration
     */
    private void applyRegisterBroker(RegisterBrokerCommand command) {
        BrokerInfo brokerInfo = BrokerInfo.builder()
                .brokerId(command.getBrokerId())
                .host(command.getHost())
                .port(command.getPort())
                .registrationTime(command.getTimestamp())
                .build();

        brokers.put(command.getBrokerId(), brokerInfo);
        log.info("Registered broker: id={}, address={}:{}, registeredAt={}",
                command.getBrokerId(), command.getHost(), command.getPort(), command.getTimestamp());
    }

    /**
     * Apply broker unregistration
     */
    private void applyUnregisterBroker(UnregisterBrokerCommand command) {
        BrokerInfo removed = brokers.remove(command.getBrokerId());
        if (removed != null) {
            log.info("Unregistered broker: id={}", command.getBrokerId());
        } else {
            log.warn("Attempted to unregister unknown broker: id={}", command.getBrokerId());
        }
    }

    /**
     * Get broker information by ID
     */
    public BrokerInfo getBroker(int brokerId) {
        return brokers.get(brokerId);
    }

    /**
     * Get all registered brokers
     */
    public Map<Integer, BrokerInfo> getAllBrokers() {
        return new ConcurrentHashMap<>(brokers);
    }

    /**
     * Get topic information by name
     */
    public TopicInfo getTopic(String topicName) {
        return topics.get(topicName);
    }

    /**
     * Get all topics
     */
    public Map<String, TopicInfo> getAllTopics() {
        return new ConcurrentHashMap<>(topics);
    }

    /**
     * Create a snapshot of current metadata state
     */
    public byte[] createSnapshot() {
        log.debug("Creating metadata state snapshot");
        
        // TODO: Serialize all topics metadata
        // TODO: Serialize all partition metadata
        // TODO: Serialize all broker metadata
        // TODO: Serialize consumer group metadata
        // TODO: Return serialized snapshot
        
        return new byte[0]; // Placeholder
    }

    /**
     * Restore metadata state from snapshot
     */
    public void restoreFromSnapshot(byte[] snapshot) {
        log.info("Restoring metadata state from snapshot");
        
        // TODO: Deserialize snapshot
        // TODO: Restore topics metadata
        // TODO: Restore partition metadata
        // TODO: Restore broker metadata
        // TODO: Restore consumer group metadata
    }
}
