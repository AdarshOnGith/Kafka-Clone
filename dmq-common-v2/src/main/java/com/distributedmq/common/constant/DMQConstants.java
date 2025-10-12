package com.distributedmq.common.constant;

/**
 * Application-wide constants.
 */
public final class DMQConstants {
    
    private DMQConstants() {
        // Utility class - prevent instantiation
    }
    
    // Service names for discovery
    public static final String SERVICE_METADATA = "dmq-metadata-service";
    public static final String SERVICE_STORAGE = "dmq-storage-service";
    public static final String SERVICE_PRODUCER = "dmq-producer-ingestion";
    public static final String SERVICE_CONSUMER = "dmq-consumer-egress";
    public static final String SERVICE_CONTROLLER = "dmq-controller-service";
    
    // Default configuration values
    public static final int DEFAULT_PARTITION_COUNT = 3;
    public static final int DEFAULT_REPLICATION_FACTOR = 3;
    public static final int DEFAULT_MIN_IN_SYNC_REPLICAS = 2;
    
    // Timeout values (milliseconds)
    public static final int DEFAULT_REQUEST_TIMEOUT_MS = 30000;  // 30 seconds
    public static final int DEFAULT_SESSION_TIMEOUT_MS = 10000;  // 10 seconds
    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 3000; // 3 seconds
    public static final int METADATA_CACHE_TTL_MS = 60000;       // 60 seconds
    
    // Batch sizes
    public static final int DEFAULT_BATCH_SIZE = 16384;          // 16 KB
    public static final int DEFAULT_MAX_REQUEST_SIZE = 1048576;  // 1 MB
    public static final int DEFAULT_FETCH_MAX_BYTES = 52428800;  // 50 MB
    
    // Replication
    public static final int REPLICA_LAG_TIME_MAX_MS = 10000;     // 10 seconds
    public static final int REPLICA_LAG_MAX_MESSAGES = 4000;
    
    // Consumer group states
    public static final String GROUP_STATE_EMPTY = "Empty";
    public static final String GROUP_STATE_PREPARING_REBALANCE = "PreparingRebalance";
    public static final String GROUP_STATE_COMPLETING_REBALANCE = "CompletingRebalance";
    public static final String GROUP_STATE_STABLE = "Stable";
    public static final String GROUP_STATE_DEAD = "Dead";
    
    // Storage node states
    public static final String NODE_STATE_ALIVE = "ALIVE";
    public static final String NODE_STATE_SUSPECT = "SUSPECT";
    public static final String NODE_STATE_DEAD = "DEAD";
    public static final String NODE_STATE_DRAINING = "DRAINING";
    
    // ACK modes
    public static final int ACKS_NONE = 0;
    public static final int ACKS_LEADER = 1;
    public static final int ACKS_ALL = -1;
    
    // Offset positions
    public static final long OFFSET_BEGINNING = -2L;
    public static final long OFFSET_END = -1L;
    
    // etcd paths
    public static final String ETCD_PATH_STORAGE_NODES = "/dmq/storage/nodes";
    public static final String ETCD_PATH_CONTROLLER_LEADER = "/dmq/controller/leader";
    public static final String ETCD_PATH_PARTITION_LEADERS = "/dmq/partitions";
    
    // HTTP Headers
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_CLIENT_ID = "X-Client-Id";
    public static final String HEADER_CONSUMER_GROUP = "X-Consumer-Group";
    
    // Rate limiting
    public static final int DEFAULT_RATE_LIMIT_PER_SECOND = 1000;
    public static final int DEFAULT_BURST_CAPACITY = 2000;
}
