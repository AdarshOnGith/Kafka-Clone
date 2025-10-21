package com.distributedmq.storage.replication;

import com.distributedmq.common.dto.PartitionStatus;
import com.distributedmq.common.dto.StorageControllerHeartbeatRequest;
import com.distributedmq.common.dto.StorageControllerHeartbeatResponse;
import com.distributedmq.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Scheduler for sending detailed heartbeats to controller for ISR management
 * Sends per-partition status including LEO, lag, HWM, and role information every 10 seconds
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageControllerHeartbeatScheduler {

    private final MetadataStore metadataStore;
    private final StorageService storageService;
    private final RestTemplate restTemplate;

    // Controller URL - should be injected from config
    private String controllerUrl;

    @PostConstruct
    public void init() {
        // TODO: Get controller URL from service discovery
        this.controllerUrl = "http://localhost:8080"; // Default for now
        log.info("Storage controller heartbeat scheduler initialized, controller URL: {}", controllerUrl);
    }

    /**
     * Send detailed heartbeat to controller every 10 seconds
     */
    @Scheduled(fixedRate = 10000) // 10 seconds
    public void sendControllerHeartbeat() {
        try {
            Integer brokerId = metadataStore.getLocalBrokerId();
            if (brokerId == null) {
                log.warn("Local broker ID not set, skipping controller heartbeat");
                return;
            }

            // Collect partition status from storage service
            List<PartitionStatus> partitionStatuses = storageService.collectPartitionStatus();

            // Build heartbeat request
            StorageControllerHeartbeatRequest request = StorageControllerHeartbeatRequest.builder()
                    .nodeId(brokerId)
                    .partitions(partitionStatuses)
                    .metadataVersion(metadataStore.getCurrentMetadataVersion())
                    .timestamp(System.currentTimeMillis())
                    .alive(true)
                    .build();

            // Send heartbeat to controller
            String endpoint = controllerUrl + "/api/v1/metadata/storage-controller-heartbeat";

            log.debug("Sending controller heartbeat for node {} with {} partitions",
                    brokerId, partitionStatuses.size());

            ResponseEntity<StorageControllerHeartbeatResponse> response = restTemplate.postForEntity(
                    endpoint, request, StorageControllerHeartbeatResponse.class);

            if (response.getBody() != null && response.getBody().isSuccess()) {
                log.debug("Controller heartbeat sent successfully for node {}", brokerId);

                // Check if we need to update metadata
                if (!response.getBody().isMetadataInSync()) {
                    log.info("Metadata out of sync for node {}, instruction: {}",
                            brokerId, response.getBody().getInstruction());
                    // TODO: Trigger metadata refresh from controller
                }
            } else {
                log.warn("Controller heartbeat failed for node {}: {}",
                        brokerId, response.getBody() != null ? response.getBody().getErrorMessage() : "null response");
            }

        } catch (Exception e) {
            log.error("Error in controller heartbeat scheduler: {}", e.getMessage());
        }
    }
}