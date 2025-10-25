package com.distributedmq.metadata.service;

import com.distributedmq.common.model.BrokerStatus;
import com.distributedmq.metadata.coordination.BrokerInfo;
import com.distributedmq.metadata.coordination.MetadataStateMachine;
import com.distributedmq.metadata.coordination.RaftController;
import com.distributedmq.metadata.coordination.UpdateBrokerStatusCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service for handling broker heartbeat monitoring (Phase 5)
 * - Receives heartbeats from storage services
 * - Periodically checks for stale heartbeats
 * - Automatically detects broker failures
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatService {

    private final RaftController raftController;
    private final MetadataStateMachine metadataStateMachine;
    private final ControllerService controllerService;

    // Heartbeat timeout configuration (broker considered dead after this time)
    @Value("${metadata.heartbeat.timeout-ms:30000}")  // Default: 30 seconds
    private long heartbeatTimeoutMs;

    // Heartbeat check interval (how often we check for stale heartbeats)
    @Value("${metadata.heartbeat.check-interval-ms:10000}")  // Default: 10 seconds
    private long heartbeatCheckIntervalMs;

    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("Heartbeat Service initialized");
        log.info("Heartbeat timeout: {} ms ({} seconds)", heartbeatTimeoutMs, heartbeatTimeoutMs / 1000);
        log.info("Heartbeat check interval: {} ms ({} seconds)", heartbeatCheckIntervalMs, heartbeatCheckIntervalMs / 1000);
        log.info("========================================");
    }

    /**
     * Process a heartbeat from a broker
     * Updates the broker's status to ONLINE and lastHeartbeatTime via Raft
     */
    public void processHeartbeat(Integer brokerId) {
        log.debug("Processing heartbeat from broker: {}", brokerId);

        // Check if broker is registered
        BrokerInfo broker = metadataStateMachine.getAllBrokers().get(brokerId);
        if (broker == null) {
            log.warn("Received heartbeat from unregistered broker: {}", brokerId);
            throw new IllegalArgumentException("Broker " + brokerId + " is not registered");
        }

        // Only the leader processes heartbeats
        if (!raftController.isControllerLeader()) {
            log.debug("Not the leader, ignoring heartbeat from broker {}", brokerId);
            return;
        }

        long now = System.currentTimeMillis();

        // Create command to update broker status via Raft
        UpdateBrokerStatusCommand command = UpdateBrokerStatusCommand.builder()
                .brokerId(brokerId)
                .status(BrokerStatus.ONLINE)
                .lastHeartbeatTime(now)
                .timestamp(now)
                .build();

        try {
            // Submit to Raft with timeout
            raftController.appendCommand(command).get(5, TimeUnit.SECONDS);
            log.debug("Heartbeat processed for broker {}, last heartbeat time updated to {}", brokerId, now);
        } catch (Exception e) {
            log.error("Failed to update heartbeat for broker {} via Raft: {}", brokerId, e.getMessage());
            throw new RuntimeException("Failed to process heartbeat", e);
        }
    }

    /**
     * Scheduled task to check for stale heartbeats and detect broker failures
     * Runs every heartbeatCheckIntervalMs (default: 10 seconds)
     */
    @Scheduled(fixedDelayString = "${metadata.heartbeat.check-interval-ms:10000}")
    public void checkHeartbeats() {
        // Only the leader checks heartbeats
        if (!raftController.isControllerLeader()) {
            log.trace("Not the leader, skipping heartbeat check");
            return;
        }

        long now = System.currentTimeMillis();
        Map<Integer, BrokerInfo> allBrokers = metadataStateMachine.getAllBrokers();

        log.debug("Checking heartbeats for {} brokers...", allBrokers.size());

        for (BrokerInfo broker : allBrokers.values()) {
            // Skip if already marked as offline
            if (broker.getStatus() == BrokerStatus.OFFLINE) {
                log.trace("Broker {} already OFFLINE, skipping", broker.getBrokerId());
                continue;
            }

            long timeSinceLastHeartbeat = now - broker.getLastHeartbeatTime();

            // Check if heartbeat is stale (timeout exceeded)
            if (timeSinceLastHeartbeat > heartbeatTimeoutMs) {
                log.warn("========================================");
                log.warn("BROKER FAILURE DETECTED!");
                log.warn("Broker {} heartbeat timeout: last heartbeat {} ms ago (threshold: {} ms)",
                        broker.getBrokerId(), timeSinceLastHeartbeat, heartbeatTimeoutMs);
                log.warn("Last heartbeat time: {}", broker.getLastHeartbeatTime());
                log.warn("Current time: {}", now);
                log.warn("========================================");

                // Trigger broker failure handling
                try {
                    controllerService.handleBrokerFailure(broker.getBrokerId());
                    log.info("Successfully triggered failure handling for broker {}", broker.getBrokerId());
                } catch (Exception e) {
                    log.error("Failed to handle broker {} failure: {}", broker.getBrokerId(), e.getMessage(), e);
                }
            } else {
                log.trace("Broker {} heartbeat OK: {} ms ago", broker.getBrokerId(), timeSinceLastHeartbeat);
            }
        }
    }

    /**
     * Get heartbeat status for monitoring/debugging
     */
    public String getHeartbeatStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Heartbeat Monitor Status:\n");
        status.append("=========================\n");
        status.append("Timeout: ").append(heartbeatTimeoutMs).append(" ms\n");
        status.append("Check Interval: ").append(heartbeatCheckIntervalMs).append(" ms\n");
        status.append("Is Leader: ").append(raftController.isControllerLeader()).append("\n\n");

        long now = System.currentTimeMillis();
        status.append("Broker Heartbeat Status:\n");

        for (BrokerInfo broker : metadataStateMachine.getAllBrokers().values()) {
            long timeSinceHeartbeat = now - broker.getLastHeartbeatTime();
            status.append(String.format("  Broker %d: status=%s, last_heartbeat=%d ms ago%s\n",
                    broker.getBrokerId(),
                    broker.getStatus(),
                    timeSinceHeartbeat,
                    timeSinceHeartbeat > heartbeatTimeoutMs ? " [STALE]" : ""));
        }

        return status.toString();
    }
}
