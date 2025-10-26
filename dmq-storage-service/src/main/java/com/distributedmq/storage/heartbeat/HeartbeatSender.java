package com.distributedmq.storage.heartbeat;

import com.distributedmq.common.dto.HeartbeatResponse;
import com.distributedmq.storage.replication.MetadataStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Heartbeat Sender Service with Metadata Version Tracking
 * 
 * Periodically sends heartbeats to the metadata service to signal this broker is alive.
 * Also performs metadata staleness detection via version checking:
 * - Checks metadata version in every heartbeat response
 * - Triggers refresh after 3 consecutive version mismatches (15 seconds)
 * - Performs periodic forced refresh every 2 minutes (configurable)
 */
@Slf4j
public class HeartbeatSender {

    private final Integer brokerId;
    private final String metadataServiceUrl;
    private final RestTemplate restTemplate;
    private final MetadataStore metadataStore;

    @Value("${dmq.storage.heartbeat.interval-ms:5000}")
    private long heartbeatIntervalMs;

    @Value("${dmq.storage.heartbeat.retry-attempts:3}")
    private int retryAttempts;

    @Value("${dmq.storage.heartbeat.retry-delay-ms:1000}")
    private long retryDelayMs;

    @Value("${storage.metadata.periodic-refresh-interval-ms:120000}")
    private long periodicRefreshIntervalMs;

    @Value("${storage.heartbeat.version-mismatch-threshold:3}")
    private int versionMismatchThreshold;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile boolean enabled = true;

    // Metadata version tracking
    private volatile int consecutiveVersionMismatches = 0;

    public HeartbeatSender(Integer brokerId, String metadataServiceUrl, RestTemplate restTemplate, MetadataStore metadataStore) {
        this.brokerId = brokerId;
        this.metadataServiceUrl = metadataServiceUrl;
        this.restTemplate = restTemplate;
        this.metadataStore = metadataStore;
    }

    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("Heartbeat Sender initialized for broker {}", brokerId);
        log.info("Metadata service URL: {}", metadataServiceUrl);
        log.info("Heartbeat interval: {} ms ({} seconds)", heartbeatIntervalMs, heartbeatIntervalMs / 1000);
        log.info("Retry attempts: {}", retryAttempts);
        log.info("Version mismatch threshold: {}", versionMismatchThreshold);
        log.info("Periodic refresh interval: {} ms ({} minutes)", periodicRefreshIntervalMs, periodicRefreshIntervalMs / 60000);
        log.info("========================================");
    }

    /**
     * Send periodic heartbeat to metadata service
     * Runs every heartbeatIntervalMs (default: 5 seconds)
     * Also checks metadata version and triggers refresh if needed
     */
    @Scheduled(fixedDelayString = "${dmq.storage.heartbeat.interval-ms:5000}", initialDelay = 5000)
    public void sendHeartbeat() {
        if (!enabled) {
            log.trace("Heartbeat sender is disabled, skipping");
            return;
        }

        String endpoint = metadataServiceUrl + "/api/v1/metadata/heartbeat/" + brokerId;
        
        log.debug("Sending heartbeat to metadata service: {}", endpoint);

        boolean success = false;
        int attempt = 0;
        HeartbeatResponse heartbeatResponse = null;

        while (!success && attempt < retryAttempts) {
            attempt++;
            
            try {
                ResponseEntity<HeartbeatResponse> response = restTemplate.postForEntity(
                    endpoint, null, HeartbeatResponse.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    success = true;
                    heartbeatResponse = response.getBody();
                    successCount.incrementAndGet();
                    log.debug("✅ Heartbeat sent successfully (attempt {}/{}): version={}", 
                        attempt, retryAttempts, heartbeatResponse.getMetadataVersion());
                } else {
                    log.warn("Heartbeat returned non-2xx status (attempt {}/{}): {}", 
                        attempt, retryAttempts, response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("Failed to send heartbeat (attempt {}/{}): {}", 
                    attempt, retryAttempts, e.getMessage());
                
                // Retry with delay if not the last attempt
                if (attempt < retryAttempts) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Heartbeat retry interrupted");
                        break;
                    }
                }
            }
        }

        if (!success) {
            failureCount.incrementAndGet();
            log.error("❌ Failed to send heartbeat after {} attempts. Metadata service may mark broker {} as OFFLINE!", 
                retryAttempts, brokerId);
            return;
        }

        // Check metadata version from heartbeat response
        if (heartbeatResponse != null && heartbeatResponse.getMetadataVersion() != null) {
            checkMetadataVersion(heartbeatResponse.getMetadataVersion());
        }

        // Check if periodic refresh is needed
        checkPeriodicRefresh();
    }

    /**
     * Check metadata version from heartbeat response
     * Triggers refresh after 3 consecutive mismatches
     */
    private void checkMetadataVersion(Long remoteVersion) {
        Long localVersion = metadataStore.getCurrentMetadataVersion();
        
        if (remoteVersion > localVersion) {
            consecutiveVersionMismatches++;
            log.warn("⚠️ Metadata version mismatch detected: local={}, remote={} (consecutive mismatches: {}/{})",
                    localVersion, remoteVersion, consecutiveVersionMismatches, versionMismatchThreshold);
            
            if (consecutiveVersionMismatches >= versionMismatchThreshold) {
                log.warn("🔄 Triggering metadata refresh after {} consecutive version mismatches", 
                    consecutiveVersionMismatches);
                triggerMetadataRefresh("version mismatch");
                consecutiveVersionMismatches = 0; // Reset after triggering refresh
            }
        } else if (remoteVersion.equals(localVersion)) {
            // Versions match - reset counter
            if (consecutiveVersionMismatches > 0) {
                log.debug("Metadata versions now match (local={}, remote={}), resetting mismatch counter from {}",
                        localVersion, remoteVersion, consecutiveVersionMismatches);
                consecutiveVersionMismatches = 0;
            }
        } else {
            // Remote version is lower than local (unusual but possible after metadata service restart)
            log.warn("⚠️ Remote metadata version ({}) is lower than local ({}). Metadata service may have restarted.",
                    remoteVersion, localVersion);
        }
    }

    /**
     * Check if periodic refresh is needed (every 2 minutes by default)
     */
    private void checkPeriodicRefresh() {
        Long lastRefreshTime = metadataStore.getLastMetadataRefreshTime();
        if (lastRefreshTime == null) {
            lastRefreshTime = System.currentTimeMillis();
        }
        
        long timeSinceRefresh = System.currentTimeMillis() - lastRefreshTime;
        
        if (timeSinceRefresh >= periodicRefreshIntervalMs) {
            log.info("🔄 Triggering periodic metadata refresh (age: {} ms, threshold: {} ms)",
                    timeSinceRefresh, periodicRefreshIntervalMs);
            triggerMetadataRefresh("periodic refresh");
        }
    }

    /**
     * Trigger metadata refresh by pulling from metadata service
     */
    private void triggerMetadataRefresh(String reason) {
        try {
            log.info("Refreshing metadata due to: {}", reason);
            metadataStore.pullInitialMetadata();
            log.info("✅ Metadata refresh completed successfully");
        } catch (Exception e) {
            log.error("Failed to refresh metadata: {}", e.getMessage(), e);
        }
    }

    /**
     * Get heartbeat statistics for monitoring
     */
    public HeartbeatStats getStats() {
        return HeartbeatStats.builder()
            .brokerId(brokerId)
            .successCount(successCount.get())
            .failureCount(failureCount.get())
            .enabled(enabled)
            .metadataServiceUrl(metadataServiceUrl)
            .heartbeatIntervalMs(heartbeatIntervalMs)
            .build();
    }

    /**
     * Enable heartbeat sending
     */
    public void enable() {
        log.info("Enabling heartbeat sender for broker {}", brokerId);
        this.enabled = true;
    }

    /**
     * Disable heartbeat sending (for graceful shutdown)
     */
    public void disable() {
        log.warn("Disabling heartbeat sender for broker {}", brokerId);
        this.enabled = false;
    }

    /**
     * Heartbeat statistics
     */
    @lombok.Builder
    @lombok.Data
    public static class HeartbeatStats {
        private final Integer brokerId;
        private final int successCount;
        private final int failureCount;
        private final boolean enabled;
        private final String metadataServiceUrl;
        private final long heartbeatIntervalMs;
    }
}
