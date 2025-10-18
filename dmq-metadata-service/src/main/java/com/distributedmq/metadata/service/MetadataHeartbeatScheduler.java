package com.distributedmq.metadata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Heartbeat scheduler for metadata services
 * Periodically sends heartbeats to the active controller to maintain sync status
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataHeartbeatScheduler {

    private final MetadataService metadataService;

    /**
     * Send heartbeat to controller every 30 seconds
     */
    @Scheduled(fixedRate = 30000) // 30 seconds
    public void sendHeartbeat() {
        try {
            var response = metadataService.sendHeartbeat();

            if (response.isSuccess()) {
                if (response.isInSync()) {
                    log.debug("Heartbeat sent successfully - service is in sync");
                } else {
                    log.info("Heartbeat sent successfully - service is out of sync, controller timestamp: {}",
                            response.getControllerMetadataTimestamp());
                }
            } else {
                log.warn("Heartbeat failed: {}", response.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Error sending heartbeat: {}", e.getMessage());
        }
    }
}