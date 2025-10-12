package com.distributedmq.metadata.controller;

import com.distributedmq.common.model.StorageNode;
import com.distributedmq.metadata.service.StorageNodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for storage node operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/metadata/nodes")
@RequiredArgsConstructor
public class StorageNodeController {
    
    private final StorageNodeService nodeService;
    
    /**
     * Register a storage node.
     * POST /api/metadata/nodes
     */
    @PostMapping
    public ResponseEntity<Void> registerNode(@RequestBody RegisterNodeRequest request) {
        log.info("REST: Register storage node - nodeId={}, host={}, port={}", 
                request.getNodeId(), request.getHost(), request.getPort());
        
        nodeService.registerNode(
                request.getNodeId(),
                request.getHost(),
                request.getPort(),
                request.getRackId()
        );
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Send heartbeat.
     * POST /api/metadata/nodes/{nodeId}/heartbeat
     */
    @PostMapping("/{nodeId}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable String nodeId) {
        log.trace("REST: Heartbeat from node={}", nodeId);
        
        nodeService.processHeartbeat(nodeId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get healthy nodes.
     * GET /api/metadata/nodes/healthy
     */
    @GetMapping("/healthy")
    public ResponseEntity<List<StorageNode>> getHealthyNodes() {
        log.debug("REST: Get healthy nodes");
        
        List<StorageNode> nodes = nodeService.getHealthyNodes();
        return ResponseEntity.ok(nodes);
    }
    
    /**
     * Get nodes by status.
     * GET /api/metadata/nodes?status={status}
     */
    @GetMapping
    public ResponseEntity<List<StorageNode>> getNodesByStatus(
            @RequestParam(required = false) String status) {
        
        log.debug("REST: Get nodes by status={}", status);
        
        List<StorageNode> nodes = status != null 
                ? nodeService.getNodesByStatus(status)
                : nodeService.getHealthyNodes();
        
        return ResponseEntity.ok(nodes);
    }
    
    /**
     * Deregister a node.
     * DELETE /api/metadata/nodes/{nodeId}
     */
    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> deregisterNode(@PathVariable String nodeId) {
        log.info("REST: Deregister node={}", nodeId);
        
        nodeService.deregisterNode(nodeId);
        return ResponseEntity.ok().build();
    }
    
    @lombok.Data
    public static class RegisterNodeRequest {
        private String nodeId;
        private String host;
        private int port;
        private String rackId;
    }
}
