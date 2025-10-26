package com.distributedmq.metadata.controller;

import com.distributedmq.metadata.service.HeartbeatService;
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

    /**
     * Receive heartbeat from a storage service (broker)
     * 
     * @param brokerId The ID of the broker sending the heartbeat
     * @return 200 OK if heartbeat processed successfully
     */
    @PostMapping("/{brokerId}")
    public ResponseEntity<String> receiveHeartbeat(@PathVariable Integer brokerId) {
        log.debug("Received heartbeat from broker: {}", brokerId);
        
        try {
            heartbeatService.processHeartbeat(brokerId);
            return ResponseEntity.ok("Heartbeat received");
        } catch (Exception e) {
            log.error("Failed to process heartbeat from broker {}: {}", brokerId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Failed to process heartbeat: " + e.getMessage());
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
