package com.distributedmq.storage.heartbeat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Heartbeat Sender Service
 * 
 * Periodically sends heartbeats to the metadata service to signal this broker is alive.
 * - Sends heartbeat every 5 seconds (configurable)
 * - First heartbeat transitions broker from OFFLINE → ONLINE
 * - Continuous heartbeats keep broker ONLINE
 * - If heartbeats stop, metadata service marks broker OFFLINE after timeout (30s)
 */
@Slf4j
public class HeartbeatSender {

    private final Integer brokerId;
    private final String metadataServiceUrl;
    private final RestTemplate restTemplate;

    @Value("${dmq.storage.heartbeat.interval-ms:5000}")
    private long heartbeatIntervalMs;

    @Value("${dmq.storage.heartbeat.retry-attempts:3}")
    private int retryAttempts;

    @Value("${dmq.storage.heartbeat.retry-delay-ms:1000}")
    private long retryDelayMs;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile boolean enabled = true;

    public HeartbeatSender(Integer brokerId, String metadataServiceUrl, RestTemplate restTemplate) {
        this.brokerId = brokerId;
        this.metadataServiceUrl = metadataServiceUrl;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("Heartbeat Sender initialized for broker {}", brokerId);
        log.info("Metadata service URL: {}", metadataServiceUrl);
        log.info("Heartbeat interval: {} ms ({} seconds)", heartbeatIntervalMs, heartbeatIntervalMs / 1000);
        log.info("Retry attempts: {}", retryAttempts);
        log.info("========================================");
    }

    /**
     * Send periodic heartbeat to metadata service
     * Runs every heartbeatIntervalMs (default: 5 seconds)
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

        while (!success && attempt < retryAttempts) {
            attempt++;
            
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(endpoint, null, String.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    success = true;
                    successCount.incrementAndGet();
                    log.debug("✅ Heartbeat sent successfully (attempt {}/{}): {}", 
                        attempt, retryAttempts, response.getBody());
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
