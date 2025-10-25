package com.distributedmq.metadata.coordination;

import com.distributedmq.common.model.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    // In-memory metadata storage (replicated via Raft consensus)
    private final Map<Integer, BrokerInfo> brokers = new ConcurrentHashMap<>();
    private final Map<String, TopicInfo> topics = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, PartitionInfo>> partitions = new ConcurrentHashMap<>();

    /**
     * Apply a committed log entry to the metadata state
     */
    public void apply(Object command) {
        if (command == null) {
            log.warn("Received null command, ignoring");
            return;
        }
        
        log.info("Applying command: {} of type {}", command, command.getClass().getSimpleName());
        
        // Handle RegisterBrokerCommand
        if (command instanceof RegisterBrokerCommand) {
            RegisterBrokerCommand cmd = (RegisterBrokerCommand) command;
            log.info("Applying RegisterBrokerCommand: brokerId={}, host={}, port={}", 
                    cmd.getBrokerId(), cmd.getHost(), cmd.getPort());
            applyRegisterBroker(cmd);
            log.info("Successfully applied RegisterBrokerCommand for broker {}", cmd.getBrokerId());
        } 
        // Handle UnregisterBrokerCommand
        else if (command instanceof UnregisterBrokerCommand) {
            applyUnregisterBroker((UnregisterBrokerCommand) command);
        }
        // Handle RegisterTopicCommand
        else if (command instanceof RegisterTopicCommand) {
            RegisterTopicCommand cmd = (RegisterTopicCommand) command;
            log.info("Applying RegisterTopicCommand: topic={}, partitions={}, replicationFactor={}", 
                    cmd.getTopicName(), cmd.getPartitionCount(), cmd.getReplicationFactor());
            applyRegisterTopic(cmd);
            log.info("Successfully applied RegisterTopicCommand for topic {}", cmd.getTopicName());
        }
        // Handle AssignPartitionsCommand
        else if (command instanceof AssignPartitionsCommand) {
            AssignPartitionsCommand cmd = (AssignPartitionsCommand) command;
            log.info("Applying AssignPartitionsCommand: topic={}, assignments={}", 
                    cmd.getTopicName(), cmd.getAssignments().size());
            applyAssignPartitions(cmd);
            log.info("Successfully applied AssignPartitionsCommand for topic {}", cmd.getTopicName());
        }
        // Handle UpdatePartitionLeaderCommand
        else if (command instanceof UpdatePartitionLeaderCommand) {
            UpdatePartitionLeaderCommand cmd = (UpdatePartitionLeaderCommand) command;
            log.info("Applying UpdatePartitionLeaderCommand: topic={}, partition={}, newLeader={}", 
                    cmd.getTopicName(), cmd.getPartitionId(), cmd.getNewLeaderId());
            applyUpdatePartitionLeader(cmd);
            log.info("Successfully applied UpdatePartitionLeaderCommand for {}-{}", 
                    cmd.getTopicName(), cmd.getPartitionId());
        }
        // Handle UpdateISRCommand
        else if (command instanceof UpdateISRCommand) {
            UpdateISRCommand cmd = (UpdateISRCommand) command;
            log.info("Applying UpdateISRCommand: topic={}, partitionId={}, newISR={}", 
                    cmd.getTopicName(), cmd.getPartitionId(), cmd.getNewISR());
            applyUpdateISR(cmd);
            log.info("Successfully applied UpdateISRCommand for partition {}-{}", 
                    cmd.getTopicName(), cmd.getPartitionId());
        }
        // Handle UpdateBrokerStatusCommand (Phase 4)
        else if (command instanceof UpdateBrokerStatusCommand) {
            UpdateBrokerStatusCommand cmd = (UpdateBrokerStatusCommand) command;
            log.info("Applying UpdateBrokerStatusCommand: brokerId={}, status={}, heartbeatTime={}", 
                    cmd.getBrokerId(), cmd.getStatus(), cmd.getLastHeartbeatTime());
            applyUpdateBrokerStatus(cmd);
            log.info("Successfully applied UpdateBrokerStatusCommand for broker {}", cmd.getBrokerId());
        }
        // Handle DeleteTopicCommand
        else if (command instanceof DeleteTopicCommand) {
            DeleteTopicCommand cmd = (DeleteTopicCommand) command;
            log.info("Applying DeleteTopicCommand: topic={}", cmd.getTopicName());
            applyDeleteTopic(cmd);
            log.info("Successfully applied DeleteTopicCommand for topic {}", cmd.getTopicName());
        }
        // Handle Map-based commands (fallback for serialization issues)
        else if (command instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) command;
            
            // Try RegisterBrokerCommand
            if (map.containsKey("brokerId") && map.containsKey("host") && map.containsKey("port")) {
                log.info("Applying Map-based RegisterBrokerCommand: {}", map);
                RegisterBrokerCommand cmd = RegisterBrokerCommand.builder()
                        .brokerId(((Number) map.get("brokerId")).intValue())
                        .host((String) map.get("host"))
                        .port(((Number) map.get("port")).intValue())
                        .timestamp(map.containsKey("timestamp") ? ((Number) map.get("timestamp")).longValue() : System.currentTimeMillis())
                        .build();
                applyRegisterBroker(cmd);
                log.info("Successfully applied Map-based RegisterBrokerCommand for broker {}", cmd.getBrokerId());
            } 
            // Try RegisterTopicCommand
            else if (map.containsKey("topicName") && map.containsKey("partitionCount") && map.containsKey("replicationFactor")) {
                log.info("Applying Map-based RegisterTopicCommand: {}", map);
                
                // Extract config if present
                @SuppressWarnings("unchecked")
                Map<String, Object> configMap = (Map<String, Object>) map.get("config");
                TopicConfig config = null;
                if (configMap != null) {
                    Long retentionMs = configMap.containsKey("retentionMs") ? ((Number) configMap.get("retentionMs")).longValue() : 604800000L;
                    Long retentionBytes = configMap.containsKey("retentionBytes") ? ((Number) configMap.get("retentionBytes")).longValue() : -1L;
                    Integer segmentBytes = configMap.containsKey("segmentBytes") ? ((Number) configMap.get("segmentBytes")).intValue() : 1073741824;
                    String compressionType = configMap.containsKey("compressionType") ? (String) configMap.get("compressionType") : "none";
                    Integer minInsyncReplicas = configMap.containsKey("minInsyncReplicas") ? ((Number) configMap.get("minInsyncReplicas")).intValue() : 1;
                    
                    config = TopicConfig.builder()
                            .retentionMs(retentionMs)
                            .retentionBytes(retentionBytes)
                            .segmentBytes(segmentBytes)
                            .compressionType(compressionType)
                            .minInsyncReplicas(minInsyncReplicas)
                            .build();
                }
                
                RegisterTopicCommand cmd = RegisterTopicCommand.builder()
                        .topicName((String) map.get("topicName"))
                        .partitionCount(((Number) map.get("partitionCount")).intValue())
                        .replicationFactor(((Number) map.get("replicationFactor")).intValue())
                        .config(config)
                        .timestamp(map.containsKey("timestamp") ? ((Number) map.get("timestamp")).longValue() : System.currentTimeMillis())
                        .build();
                applyRegisterTopic(cmd);
                log.info("Successfully applied Map-based RegisterTopicCommand for topic {}", cmd.getTopicName());
            }
            // Try AssignPartitionsCommand
            else if (map.containsKey("topicName") && map.containsKey("assignments")) {
                log.info("Applying Map-based AssignPartitionsCommand: {}", map);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> assignmentsList = (List<Map<String, Object>>) map.get("assignments");
                List<PartitionAssignment> assignments = new ArrayList<>();
                
                for (Map<String, Object> assignmentMap : assignmentsList) {
                    @SuppressWarnings("unchecked")
                    List<Integer> replicaIds = ((List<Number>) assignmentMap.get("replicaIds")).stream()
                            .map(Number::intValue)
                            .collect(Collectors.toList());
                    @SuppressWarnings("unchecked")
                    List<Integer> isrIds = ((List<Number>) assignmentMap.get("isrIds")).stream()
                            .map(Number::intValue)
                            .collect(Collectors.toList());
                    
                    PartitionAssignment assignment = PartitionAssignment.builder()
                            .partitionId(((Number) assignmentMap.get("partitionId")).intValue())
                            .leaderId(((Number) assignmentMap.get("leaderId")).intValue())
                            .replicaIds(replicaIds)
                            .isrIds(isrIds)
                            .build();
                    assignments.add(assignment);
                }
                
                AssignPartitionsCommand cmd = AssignPartitionsCommand.builder()
                        .topicName((String) map.get("topicName"))
                        .assignments(assignments)
                        .timestamp(map.containsKey("timestamp") ? ((Number) map.get("timestamp")).longValue() : System.currentTimeMillis())
                        .build();
                applyAssignPartitions(cmd);
                log.info("Successfully applied Map-based AssignPartitionsCommand for topic {}", cmd.getTopicName());
            }
            // Try DeleteTopicCommand (has topicName but NOT partitionCount/replicationFactor/assignments)
            else if (map.containsKey("topicName") && 
                     !map.containsKey("partitionCount") && 
                     !map.containsKey("replicationFactor") && 
                     !map.containsKey("assignments") &&
                     !map.containsKey("partitionId")) {
                log.info("Applying Map-based DeleteTopicCommand: {}", map);
                
                DeleteTopicCommand cmd = DeleteTopicCommand.builder()
                        .topicName((String) map.get("topicName"))
                        .timestamp(map.containsKey("timestamp") ? ((Number) map.get("timestamp")).longValue() : System.currentTimeMillis())
                        .build();
                applyDeleteTopic(cmd);
                log.info("Successfully applied Map-based DeleteTopicCommand for topic {}", cmd.getTopicName());
            }
            // Try UnregisterBrokerCommand
            else if (map.containsKey("brokerId") && !map.containsKey("host") && !map.containsKey("port")) {
                log.info("Applying Map-based UnregisterBrokerCommand: {}", map);
                
                UnregisterBrokerCommand cmd = UnregisterBrokerCommand.builder()
                        .brokerId(((Number) map.get("brokerId")).intValue())
                        .timestamp(map.containsKey("timestamp") ? ((Number) map.get("timestamp")).longValue() : System.currentTimeMillis())
                        .build();
                applyUnregisterBroker(cmd);
                log.info("Successfully applied Map-based UnregisterBrokerCommand for broker {}", cmd.getBrokerId());
            }
            // Try UpdatePartitionLeaderCommand
            else if (map.containsKey("topicName") && map.containsKey("partitionId") && 
                     map.containsKey("newLeaderId") && map.containsKey("leaderEpoch")) {
                log.info("Applying Map-based UpdatePartitionLeaderCommand: {}", map);
                
                UpdatePartitionLeaderCommand cmd = UpdatePartitionLeaderCommand.builder()
                        .topicName((String) map.get("topicName"))
                        .partitionId(((Number) map.get("partitionId")).intValue())
                        .newLeaderId(((Number) map.get("newLeaderId")).intValue())
                        .leaderEpoch(((Number) map.get("leaderEpoch")).longValue())
                        .timestamp(map.containsKey("timestamp") ? ((Number) map.get("timestamp")).longValue() : System.currentTimeMillis())
                        .build();
                applyUpdatePartitionLeader(cmd);
                log.info("Successfully applied Map-based UpdatePartitionLeaderCommand for {}-{}", 
                        cmd.getTopicName(), cmd.getPartitionId());
            }
            // Try UpdateISRCommand
            else if (map.containsKey("topicName") && map.containsKey("partitionId") && map.containsKey("newISR")) {
                log.info("Applying Map-based UpdateISRCommand: {}", map);
                
                @SuppressWarnings("unchecked")
                List<Integer> newISR = ((List<Number>) map.get("newISR")).stream()
                        .map(Number::intValue)
                        .collect(Collectors.toList());
                
                UpdateISRCommand cmd = UpdateISRCommand.builder()
                        .topicName((String) map.get("topicName"))
                        .partitionId(((Number) map.get("partitionId")).intValue())
                        .newISR(newISR)
                        .timestamp(map.containsKey("timestamp") ? ((Number) map.get("timestamp")).longValue() : System.currentTimeMillis())
                        .build();
                applyUpdateISR(cmd);
                log.info("Successfully applied Map-based UpdateISRCommand for {}-{}", 
                        cmd.getTopicName(), cmd.getPartitionId());
            }
            else {
                log.error("Unknown Map command structure: {}", map);
            }
        }
        else {
            log.error("Unknown command type: {} - {}", command.getClass().getSimpleName(), command);
            log.error("Command toString: {}", command.toString());
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
                .status(com.distributedmq.common.model.BrokerStatus.ONLINE)  // Initially ONLINE
                .lastHeartbeatTime(command.getTimestamp())  // Registration time as first heartbeat
                .build();

        brokers.put(command.getBrokerId(), brokerInfo);
        log.info("Registered broker: id={}, address={}:{}, status=ONLINE, registeredAt={}",
                command.getBrokerId(), command.getHost(), command.getPort(), command.getTimestamp());
    }

    /**
     * Apply broker status update (Phase 4)
     * Updates broker status and heartbeat timestamp
     */
    private void applyUpdateBrokerStatus(UpdateBrokerStatusCommand command) {
        BrokerInfo broker = brokers.get(command.getBrokerId());
        if (broker == null) {
            log.warn("Cannot update status for unknown broker: id={}", command.getBrokerId());
            return;
        }

        broker.setStatus(command.getStatus());
        broker.setLastHeartbeatTime(command.getLastHeartbeatTime());
        
        log.info("Updated broker status: id={}, status={}, lastHeartbeat={}",
                command.getBrokerId(), command.getStatus(), command.getLastHeartbeatTime());
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
     * Apply topic registration
     */
    private void applyRegisterTopic(RegisterTopicCommand command) {
        TopicInfo topicInfo = TopicInfo.builder()
                .topicName(command.getTopicName())
                .partitionCount(command.getPartitionCount())
                .replicationFactor(command.getReplicationFactor())
                .config(command.getConfig())
                .createdAt(command.getTimestamp())
                .build();

        topics.put(command.getTopicName(), topicInfo);
        log.info("Registered topic: name={}, partitions={}, replicationFactor={}, createdAt={}",
                command.getTopicName(), command.getPartitionCount(), 
                command.getReplicationFactor(), command.getTimestamp());
    }

    /**
     * Apply partition assignments
     */
    private void applyAssignPartitions(AssignPartitionsCommand command) {
        Map<Integer, PartitionInfo> topicPartitions = partitions.computeIfAbsent(
                command.getTopicName(), k -> new ConcurrentHashMap<>());

        for (PartitionAssignment assignment : command.getAssignments()) {
            PartitionInfo partitionInfo = PartitionInfo.builder()
                    .topicName(command.getTopicName())
                    .partitionId(assignment.getPartitionId())
                    .leaderId(assignment.getLeaderId())
                    .replicaIds(assignment.getReplicaIds())
                    .isrIds(assignment.getIsrIds())
                    .startOffset(0L)
                    .endOffset(0L)
                    .leaderEpoch(0L)
                    .build();

            topicPartitions.put(assignment.getPartitionId(), partitionInfo);
            log.debug("Assigned partition: topic={}, partition={}, leader={}, replicas={}, isr={}",
                    command.getTopicName(), assignment.getPartitionId(), 
                    assignment.getLeaderId(), assignment.getReplicaIds(), assignment.getIsrIds());
        }

        log.info("Assigned {} partitions for topic: {}", 
                command.getAssignments().size(), command.getTopicName());
    }

    /**
     * Apply partition leader update
     */
    private void applyUpdatePartitionLeader(UpdatePartitionLeaderCommand command) {
        Map<Integer, PartitionInfo> topicPartitions = partitions.get(command.getTopicName());
        if (topicPartitions == null) {
            log.warn("Cannot update partition leader - topic not found: {}", command.getTopicName());
            return;
        }

        PartitionInfo partition = topicPartitions.get(command.getPartitionId());
        if (partition == null) {
            log.warn("Cannot update partition leader - partition not found: {}-{}", 
                    command.getTopicName(), command.getPartitionId());
            return;
        }

        partition.setLeaderId(command.getNewLeaderId());
        partition.setLeaderEpoch(command.getLeaderEpoch());
        log.info("Updated partition leader: topic={}, partition={}, newLeader={}, epoch={}",
                command.getTopicName(), command.getPartitionId(), 
                command.getNewLeaderId(), command.getLeaderEpoch());
    }

    /**
     * Apply ISR update
     */
    private void applyUpdateISR(UpdateISRCommand command) {
        Map<Integer, PartitionInfo> topicPartitions = partitions.get(command.getTopicName());
        if (topicPartitions == null) {
            log.warn("Cannot update ISR - topic not found: {}", command.getTopicName());
            return;
        }

        PartitionInfo partition = topicPartitions.get(command.getPartitionId());
        if (partition == null) {
            log.warn("Cannot update ISR - partition not found: {}-{}", 
                    command.getTopicName(), command.getPartitionId());
            return;
        }

        partition.setIsrIds(command.getNewISR());
        log.info("Updated ISR: topic={}, partition={}, newISR={}",
                command.getTopicName(), command.getPartitionId(), command.getNewISR());
    }

    /**
     * Apply topic deletion
     */
    private void applyDeleteTopic(DeleteTopicCommand command) {
        TopicInfo removed = topics.remove(command.getTopicName());
        if (removed != null) {
            // Also remove all partition information
            partitions.remove(command.getTopicName());
            log.info("Deleted topic: name={}", command.getTopicName());
        } else {
            log.warn("Attempted to delete unknown topic: name={}", command.getTopicName());
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
     * Get partition information for a specific partition
     */
    public PartitionInfo getPartition(String topicName, int partitionId) {
        Map<Integer, PartitionInfo> topicPartitions = partitions.get(topicName);
        if (topicPartitions == null) {
            return null;
        }
        return topicPartitions.get(partitionId);
    }

    /**
     * Get all partitions for a topic
     */
    public Map<Integer, PartitionInfo> getPartitions(String topicName) {
        Map<Integer, PartitionInfo> topicPartitions = partitions.get(topicName);
        if (topicPartitions == null) {
            return new ConcurrentHashMap<>();
        }
        return new ConcurrentHashMap<>(topicPartitions);
    }

    /**
     * Get all partitions across all topics
     */
    public Map<String, Map<Integer, PartitionInfo>> getAllPartitions() {
        return new ConcurrentHashMap<>(partitions);
    }

    /**
     * Check if a topic exists
     */
    public boolean topicExists(String topicName) {
        return topics.containsKey(topicName);
    }

    /**
     * Check if a broker exists
     */
    public boolean brokerExists(int brokerId) {
        return brokers.containsKey(brokerId);
    }

    /**
     * Get count of registered brokers
     */
    public int getBrokerCount() {
        return brokers.size();
    }

    /**
     * Get count of topics
     */
    public int getTopicCount() {
        return topics.size();
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
