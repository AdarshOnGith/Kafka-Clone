package com.distributedmq.storage.service;

import com.distributedmq.common.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Heartbeat Service - sends periodic heartbeats to Metadata Service.
 * Implements Flow 3 Step 1: Storage nodes send heartbeats.
 */
@Slf4j
@Service
public class HeartbeatService {
    
    @GrpcClient("metadata-service")
    private MetadataServiceGrpc.MetadataServiceBlockingStub metadataServiceStub;
    
    @Value("${dmq.storage.node-id}")
    private String nodeId;
    
    @Value("${server.port}")
    private int restPort;
    
    @Value("${grpc.server.port}")
    private int grpcPort;
    
    @Value("${dmq.storage.rack-id:rack-1}")
    private String rackId;
    
    private volatile boolean registered = false;
    
    /**
     * Register this storage node with Metadata Service on startup.
     */
    @PostConstruct
    public void registerNode() {
        try {
            log.info("Registering storage node: nodeId={}, grpcPort={}", nodeId, grpcPort);
            
            RegisterNodeRequest request = RegisterNodeRequest.newBuilder()
                    .setNodeId(nodeId)
                    .setHost("localhost") // TODO: Get actual host
                    .setPort(grpcPort)
                    .setRackId(rackId)
                    .build();
            
            RegisterNodeResponse response = metadataServiceStub.registerStorageNode(request);
            
            if (response.getSuccess()) {
                registered = true;
                log.info("Successfully registered storage node: {}", nodeId);
            } else {
                log.error("Failed to register storage node: errorCode={}", response.getErrorCode());
            }
            
        } catch (Exception e) {
            log.error("Exception during node registration", e);
            // Will retry via scheduled heartbeat
        }
    }
    
    /**
     * Send periodic heartbeat to Metadata Service.
     * Runs every 3 seconds (configurable).
     */
    @Scheduled(fixedDelayString = "${dmq.storage.metadata.heartbeat-interval-ms:3000}")
    public void sendHeartbeat() {
        try {
            // Re-register if not registered yet
            if (!registered) {
                registerNode();
                return;
            }
            
            log.trace("Sending heartbeat for node: {}", nodeId);
            
            HeartbeatRequest request = HeartbeatRequest.newBuilder()
                    .setNodeId(nodeId)
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            HeartbeatResponse response = metadataServiceStub.nodeHeartbeat(request);
            
            if (!response.getSuccess()) {
                log.warn("Heartbeat failed for node: {}", nodeId);
                registered = false;
            }
            
        } catch (Exception e) {
            log.error("Exception during heartbeat", e);
            registered = false;
        }
    }
    
    /**
     * Deregister node on shutdown.
     */
    @PreDestroy
    public void deregisterNode() {
        log.info("Deregistering storage node: {}", nodeId);
        // Metadata service will mark node as DEAD after missing heartbeats
    }
}
