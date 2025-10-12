package com.distributedmq.controller.controller;

import com.distributedmq.controller.election.ControllerLeaderElection;
import com.distributedmq.controller.service.FailureDetectionService;
import com.distributedmq.controller.service.LeaderElectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for controller operations.
 * Provides monitoring and manual intervention endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/controller")
@RequiredArgsConstructor
public class ControllerController {
    
    private final ControllerLeaderElection leaderElection;
    private final FailureDetectionService failureDetection;
    private final LeaderElectionService electionService;
    
    /**
     * Get controller status.
     * GET /api/controller/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("controllerId", leaderElection.getControllerId());
        status.put("isLeader", leaderElection.isLeader());
        status.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Manually trigger failure detection.
     * POST /api/controller/detect-failures
     */
    @PostMapping("/detect-failures")
    public ResponseEntity<Map<String, String>> triggerFailureDetection() {
        log.info("Manual trigger: failure detection");
        
        if (!leaderElection.isLeader()) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "This controller is not the leader");
            return ResponseEntity.status(403).body(response);
        }
        
        failureDetection.triggerFailureDetection();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Failure detection triggered");
        return ResponseEntity.ok(response);
    }
    
    /**
     * Manually trigger leader election for a partition.
     * POST /api/controller/elect-leader
     */
    @PostMapping("/elect-leader")
    public ResponseEntity<Map<String, Object>> electLeader(
            @RequestBody ElectLeaderRequest request) {
        
        log.info("Manual trigger: leader election for {}-{}", request.getTopic(), request.getPartition());
        
        if (!leaderElection.isLeader()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "This controller is not the leader");
            return ResponseEntity.status(403).body(response);
        }
        
        try {
            electionService.electNewLeader(request.getTopic(), request.getPartition());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("topic", request.getTopic());
            response.put("partition", request.getPartition());
            response.put("message", "Leader election completed");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to elect leader", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Step down as leader (for testing).
     * POST /api/controller/step-down
     */
    @PostMapping("/step-down")
    public ResponseEntity<Map<String, String>> stepDown() {
        log.info("Manual trigger: step down as leader");
        
        leaderElection.stepDown();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Controller stepped down");
        
        return ResponseEntity.ok(response);
    }
    
    @lombok.Data
    public static class ElectLeaderRequest {
        private String topic;
        private int partition;
    }
}
