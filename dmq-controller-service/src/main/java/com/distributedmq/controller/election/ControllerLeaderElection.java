package com.distributedmq.controller.election;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Lock;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.lock.LockResponse;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller Leader Election Service.
 * Uses etcd for distributed leader election.
 * Only one controller can be leader at a time.
 * 
 * Algorithm:
 * 1. Try to acquire lock in etcd
 * 2. If successful, become leader
 * 3. Keep lease alive with periodic refresh
 * 4. If lock lost, step down as leader
 * 5. Retry election after timeout
 */
@Slf4j
@Service
public class ControllerLeaderElection {
    
    @Value("${dmq.controller.controller-id}")
    private String controllerId;
    
    @Value("${dmq.controller.etcd.endpoints:http://localhost:2379}")
    private String etcdEndpoints;
    
    @Value("${dmq.controller.election.timeout-seconds:30}")
    private long electionTimeoutSeconds;
    
    private Client etcdClient;
    private Lock lockClient;
    private Lease leaseClient;
    
    private long leaseId = -1;
    private ByteSequence lockKey;
    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    @PostConstruct
    public void initialize() {
        try {
            // Create etcd client
            etcdClient = Client.builder()
                    .endpoints(etcdEndpoints.split(","))
                    .build();
            
            lockClient = etcdClient.getLockClient();
            leaseClient = etcdClient.getLeaseClient();
            
            lockKey = ByteSequence.from("/dmq/controller/leader", StandardCharsets.UTF_8);
            
            log.info("etcd client initialized: endpoints={}", etcdEndpoints);
            
            // Start leader election
            startLeaderElection();
            
        } catch (Exception e) {
            log.error("Failed to initialize etcd client", e);
        }
    }
    
    /**
     * Start the leader election process.
     */
    private void startLeaderElection() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (!isLeader.get()) {
                    attemptLeaderElection();
                }
            } catch (Exception e) {
                log.error("Error in leader election", e);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }
    
    /**
     * Attempt to become the leader.
     */
    private void attemptLeaderElection() {
        try {
            log.debug("Attempting leader election for controller: {}", controllerId);
            
            // Create a lease
            leaseId = leaseClient.grant(electionTimeoutSeconds).get().getID();
            
            // Try to acquire lock
            ByteSequence lockValue = ByteSequence.from(controllerId, StandardCharsets.UTF_8);
            LockResponse lockResponse = lockClient.lock(lockKey, leaseId).get();
            
            if (lockResponse != null) {
                isLeader.set(true);
                log.info("✅ Controller {} became LEADER", controllerId);
                
                // Keep lease alive
                keepLeaseAlive();
            }
            
        } catch (InterruptedException | ExecutionException e) {
            log.debug("Failed to acquire leader lock: {}", e.getMessage());
            isLeader.set(false);
        }
    }
    
    /**
     * Keep the lease alive to maintain leadership.
     */
    private void keepLeaseAlive() {
        StreamObserver<LeaseKeepAliveResponse> observer = new StreamObserver<LeaseKeepAliveResponse>() {
            @Override
            public void onNext(LeaseKeepAliveResponse response) {
                log.trace("Lease keep-alive response: TTL={}", response.getTTL());
            }
            
            @Override
            public void onError(Throwable t) {
                log.error("Lease keep-alive error, stepping down as leader", t);
                isLeader.set(false);
                
                // Release lock
                try {
                    lockClient.unlock(lockKey).get();
                } catch (Exception e) {
                    log.error("Failed to unlock", e);
                }
            }
            
            @Override
            public void onCompleted() {
                log.info("Lease keep-alive completed");
            }
        };
        
        leaseClient.keepAlive(leaseId, observer);
    }
    
    /**
     * Check if this controller is the leader.
     */
    public boolean isLeader() {
        return isLeader.get();
    }
    
    /**
     * Get the controller ID.
     */
    public String getControllerId() {
        return controllerId;
    }
    
    /**
     * Step down as leader (for testing or graceful shutdown).
     */
    public void stepDown() {
        if (isLeader.get()) {
            log.info("Controller {} stepping down as leader", controllerId);
            
            try {
                lockClient.unlock(lockKey).get();
                leaseClient.revoke(leaseId).get();
            } catch (Exception e) {
                log.error("Error stepping down", e);
            }
            
            isLeader.set(false);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        stepDown();
        scheduler.shutdown();
        
        if (etcdClient != null) {
            etcdClient.close();
        }
    }
}
