package com.distributedmq.storage.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import lombok.Data;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Centralized configuration for DMQ Storage Service
 * Contains all constants and configurable values
 */
@Configuration
@ConfigurationProperties(prefix = "dmq.storage")
@Data
public class StorageConfig {

    // ========== BROKER CONFIGURATION ==========
    private BrokerConfig broker = new BrokerConfig();

    @Data
    public static class BrokerConfig {
        private Integer id = 1;
        private String host = "localhost";
        private Integer port = 8081;  // Default port, can be overridden by BROKER_PORT env var
        private String dataDir;

        /**
         * Set the data directory based on broker ID to avoid conflicts
         */
        public void setId(Integer id) {
            this.id = id;
            if (this.dataDir == null || this.dataDir.isEmpty()) {
                this.dataDir = "./data/broker-" + id;
            }
        }

        /**
         * Allow explicit data directory setting via environment variable
         */
        public void setDataDir(String dataDir) {
            this.dataDir = dataDir;
        }
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
        private Long segmentSizeBytes = 1073741824L; // 1GB
        private Integer flushIntervalMs = 1000;
        private Long retentionCheckIntervalMs = 300000L; // 5 minutes
        private String dataDir = "./data";
        private String logsDir = "logs";
    }

    // ========== REPLICATION CONFIGURATION ==========
    private ReplicationConfig replication = new ReplicationConfig();

    @Data
    public static class ReplicationConfig {
        private Integer fetchMaxBytes = 1048576; // 1MB
        private Integer fetchMaxWaitMs = 500;
        private Long replicaLagTimeMaxMs = 10000L;
    }

    // ========== CONSUMER CONFIGURATION ==========
    private ConsumerConfig consumer = new ConsumerConfig();

    @Data
    public static class ConsumerConfig {
        private Integer defaultMaxMessages = 100;
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

    // ========== REPLICATION CONSTANTS ==========
    // Acknowledgment levels
    public static final int ACKS_NONE = 0;        // No acknowledgment required
    public static final int ACKS_LEADER = 1;      // Leader acknowledgment required
    public static final int ACKS_ALL = -1;        // All ISR acknowledgment required

    // Replication settings
    public static final int FOLLOWER_ACKS = 0;    // Followers don't replicate further

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

    /**
     * Customize Jackson ObjectMapper to handle unknown enum values
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder.featuresToEnable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
    }

    /**
     * Ensure broker data directory is always set
     */
    @PostConstruct
    public void initializeBrokerDataDir() {
        if (broker.getDataDir() == null || broker.getDataDir().isEmpty()) {
            broker.setDataDir("./data/broker-" + broker.getId());
        }
    }
}