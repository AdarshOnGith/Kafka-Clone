package com.distributedmq.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized configuration for DMQ Storage Service
 * Contains all constants and configurable values
 */
@Configuration
@ConfigurationProperties(prefix = "dmq.storage")
@Data
public class StorageConfig {

    private int serverPort = 8082;

    // ========== BROKER CONFIGURATION ==========
    private BrokerConfig broker = new BrokerConfig();

    @Data
    public static class BrokerConfig {
        private int id = 1;
        private String host = "localhost";
        private int port = 9092;
        private String dataDir = "./data/broker-1";
    }

    // ========== METADATA SERVICE CONFIGURATION ==========
    private MetadataConfig metadata = new MetadataConfig();

    @Data
    public static class MetadataConfig {
        private String serviceUrl = "http://localhost:8081";
    }

    // ========== WAL CONFIGURATION ==========
    private WalConfig wal = new WalConfig();

    @Data
    public static class WalConfig {
        private long segmentSizeBytes = 1073741824L; // 1GB
        private int flushIntervalMs = 1000;
        private long retentionCheckIntervalMs = 300000; // 5 minutes
        private String dataDir = "./data";
        private String logsDir = "logs";
    }

    // ========== REPLICATION CONFIGURATION ==========
    private ReplicationConfig replication = new ReplicationConfig();

    @Data
    public static class ReplicationConfig {
        private int fetchMaxBytes = 1048576; // 1MB
        private int fetchMaxWaitMs = 500;
        private long replicaLagTimeMaxMs = 10000;
    }

    // ========== CONSUMER CONFIGURATION ==========
    private ConsumerConfig consumer = new ConsumerConfig();

    @Data
    public static class ConsumerConfig {
        private int defaultMaxMessages = 100;
    }

    // ========== CONSTANTS ==========

    // Log segment constants
    public static final String LOG_FILE_FORMAT = "%020d.log";
    public static final String LOG_FILE_EXTENSION = ".log";

    // Serialization constants
    public static final int NULL_KEY_LENGTH = -1;
    public static final int CRC_SIZE = 4;
    public static final int OFFSET_SIZE = 8;
    public static final int TIMESTAMP_SIZE = 8;
    public static final int LENGTH_SIZE = 4;

    // Message format version
    public static final int MESSAGE_FORMAT_VERSION = 1;

    // Default values
    public static final long DEFAULT_OFFSET = 0L;
    public static final long DEFAULT_HIGH_WATER_MARK = 0L;
    public static final long DEFAULT_LOG_END_OFFSET = 0L;

    // ========== DERIVED PATHS ==========

    /**
     * Get the full path for WAL data directory
     */
    public String getWalDataDir() {
        return wal.getDataDir();
    }

    /**
     * Get the full path for logs directory
     */
    public String getLogsDir() {
        return wal.getDataDir() + "/" + wal.getLogsDir();
    }

    /**
     * Get the broker-specific data directory
     */
    public String getBrokerDataDir() {
        return broker.getDataDir();
    }

    /**
     * Get the broker-specific logs directory
     */
    public String getBrokerLogsDir() {
        return broker.getDataDir() + "/" + wal.getLogsDir();
    }
}