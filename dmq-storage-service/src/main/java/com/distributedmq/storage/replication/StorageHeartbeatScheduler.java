package com.distributedmq.storage.replication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Scheduler for sending periodic heartbeats to controller
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageHeartbeatScheduler {

    private final MetadataStore metadataStore;

    @PostConstruct
    public void init() {
        log.info("Storage heartbeat scheduler initialized");
    }

    /**
     * Send heartbeat to controller every 5 seconds
     */
    @Scheduled(fixedRate = 5000)
    public void sendHeartbeat() {
        try {
            metadataStore.sendHeartbeatToController();
        } catch (Exception e) {
            log.error("Error in heartbeat scheduler: {}", e.getMessage());
        }
    }
}