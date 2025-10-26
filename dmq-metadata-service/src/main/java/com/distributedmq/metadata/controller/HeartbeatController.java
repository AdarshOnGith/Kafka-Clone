package com.distributedmq.metadata.controller;

import com.distributedmq.common.dto.HeartbeatResponse;
import com.distributedmq.metadata.service.HeartbeatService;
import com.distributedmq.metadata.service.MetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for broker heartbeat monitoring (Phase 5)
 * Storage services call this endpoint periodically to signal they are alive
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/metadata/heartbeat")
@RequiredArgsConstructor
public class HeartbeatController {

    private final HeartbeatService heartbeatService;
    private final MetadataService metadataService;

    /**
     * Receive heartbeat from a storage service (broker)
     * Returns current metadata version for staleness detection
     * 
     * @param brokerId The ID of the broker sending the heartbeat
     * @return HeartbeatResponse with success status and current metadata version
     */
    @PostMapping("/{brokerId}")
    public ResponseEntity<HeartbeatResponse> receiveHeartbeat(@PathVariable Integer brokerId) {
        log.debug("Received heartbeat from broker: {}", brokerId);
        
        try {
            heartbeatService.processHeartbeat(brokerId);
            
            // Get current metadata version
            long metadataVersion = metadataService.getMetadataVersion();
            
            HeartbeatResponse response = HeartbeatResponse.builder()
                    .success(true)
                    .metadataVersion(metadataVersion)
                    .message("Heartbeat received")
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            log.debug("Heartbeat ACK for broker {}: version={}", brokerId, metadataVersion);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to process heartbeat from broker {}: {}", brokerId, e.getMessage(), e);
            
            HeartbeatResponse errorResponse = HeartbeatResponse.builder()
                    .success(false)
                    .message("Failed to process heartbeat: " + e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Health check endpoint for the heartbeat service
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Heartbeat service is running");
    }

    /**
     * Get heartbeat monitoring status for all brokers
     * Useful for debugging and monitoring
     */
    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        try {
            String status = heartbeatService.getHeartbeatStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get heartbeat status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Failed to get status: " + e.getMessage());
        }
    }
}
