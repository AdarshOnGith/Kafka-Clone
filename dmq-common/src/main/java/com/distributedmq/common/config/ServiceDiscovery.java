package com.distributedmq.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Service discovery utility that reads from centralized config file
 */
@Slf4j
public class ServiceDiscovery {

    private static final String CONFIG_FILE_PATH = "config/services.json";
    private static ServiceConfig config;

    static {
        loadConfig();
    }

    public static void loadConfig() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            config = mapper.readValue(new File(CONFIG_FILE_PATH), ServiceConfig.class);
            log.info("Loaded service configuration from {}", CONFIG_FILE_PATH);
        } catch (IOException e) {
            log.error("Failed to load service configuration: {}", e.getMessage());
            // Create default config if file doesn't exist
            config = createDefaultConfig();
        }
    }

    private static ServiceConfig createDefaultConfig() {
        ServiceConfig defaultConfig = new ServiceConfig();

        // Default metadata services
        ServiceInfo ms1 = new ServiceInfo();
        ms1.setId(0);
        ms1.setHost("localhost");
        ms1.setPort(8080);
        ms1.setUrl("http://localhost:8080");

        ServiceInfo ms2 = new ServiceInfo();
        ms2.setId(1);
        ms2.setHost("localhost");
        ms2.setPort(8081);
        ms2.setUrl("http://localhost:8081");

        ServiceInfo ms3 = new ServiceInfo();
        ms3.setId(2);
        ms3.setHost("localhost");
        ms3.setPort(8082);
        ms3.setUrl("http://localhost:8082");

        defaultConfig.getServices().getMetadataServices().add(ms1);
        defaultConfig.getServices().getMetadataServices().add(ms2);
        defaultConfig.getServices().getMetadataServices().add(ms3);

        // Default storage services
        StorageServiceInfo ss1 = new StorageServiceInfo();
        ss1.setId(100);
        ss1.setHost("localhost");
        ss1.setPort(9090);
        ss1.setUrl("http://localhost:9090");
        ss1.setPairedMetadataServiceId(0);

        StorageServiceInfo ss2 = new StorageServiceInfo();
        ss2.setId(101);
        ss2.setHost("localhost");
        ss2.setPort(9091);
        ss2.setUrl("http://localhost:9091");
        ss2.setPairedMetadataServiceId(1);

        StorageServiceInfo ss3 = new StorageServiceInfo();
        ss3.setId(102);
        ss3.setHost("localhost");
        ss3.setPort(9092);
        ss3.setUrl("http://localhost:9092");
        ss3.setPairedMetadataServiceId(2);

        defaultConfig.getServices().getStorageServices().add(ss1);
        defaultConfig.getServices().getStorageServices().add(ss2);
        defaultConfig.getServices().getStorageServices().add(ss3);

        return defaultConfig;
    }

    public static String getMetadataServiceUrl(Integer serviceId) {
        return config.getServices().getMetadataServices().stream()
                .filter(s -> s.getId().equals(serviceId))
                .findFirst()
                .map(ServiceInfo::getUrl)
                .orElse(null);
    }

    public static String getStorageServiceUrl(Integer serviceId) {
        return config.getServices().getStorageServices().stream()
                .filter(s -> s.getId().equals(serviceId))
                .findFirst()
                .map(StorageServiceInfo::getUrl)
                .orElse(null);
    }

    public static Integer getPairedMetadataServiceId(Integer storageServiceId) {
        return config.getServices().getStorageServices().stream()
                .filter(s -> s.getId().equals(storageServiceId))
                .findFirst()
                .map(StorageServiceInfo::getPairedMetadataServiceId)
                .orElse(null);
    }

    public static List<ServiceInfo> getAllMetadataServices() {
        return config.getServices().getMetadataServices();
    }

    public static List<StorageServiceInfo> getAllStorageServices() {
        return config.getServices().getStorageServices();
    }

    public static ServiceInfo getMetadataServiceById(Integer id) {
        return config.getServices().getMetadataServices().stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public static StorageServiceInfo getStorageServiceById(Integer id) {
        return config.getServices().getStorageServices().stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    // Config classes
    public static class ServiceConfig {
        private Services services = new Services();
        private ControllerConfig controller = new ControllerConfig();
        private MetadataConfig metadata = new MetadataConfig();

        public Services getServices() { return services; }
        public void setServices(Services services) { this.services = services; }
        public ControllerConfig getController() { return controller; }
        public void setController(ControllerConfig controller) { this.controller = controller; }
        public MetadataConfig getMetadata() { return metadata; }
        public void setMetadata(MetadataConfig metadata) { this.metadata = metadata; }
    }

    public static class Services {
        private List<ServiceInfo> metadataServices = new java.util.ArrayList<>();
        private List<StorageServiceInfo> storageServices = new java.util.ArrayList<>();

        public List<ServiceInfo> getMetadataServices() { return metadataServices; }
        public void setMetadataServices(List<ServiceInfo> metadataServices) { this.metadataServices = metadataServices; }
        public List<StorageServiceInfo> getStorageServices() { return storageServices; }
        public void setStorageServices(List<StorageServiceInfo> storageServices) { this.storageServices = storageServices; }
    }

    public static class ServiceInfo {
        private Integer id;
        private String host;
        private Integer port;
        private String url;

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public static class StorageServiceInfo extends ServiceInfo {
        private Integer pairedMetadataServiceId;

        public Integer getPairedMetadataServiceId() { return pairedMetadataServiceId; }
        public void setPairedMetadataServiceId(Integer pairedMetadataServiceId) { this.pairedMetadataServiceId = pairedMetadataServiceId; }
    }

    public static class ControllerConfig {
        private Integer electionTimeoutMs = 5000;
        private Integer heartbeatIntervalMs = 1000;

        public Integer getElectionTimeoutMs() { return electionTimeoutMs; }
        public void setElectionTimeoutMs(Integer electionTimeoutMs) { this.electionTimeoutMs = electionTimeoutMs; }
        public Integer getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
        public void setHeartbeatIntervalMs(Integer heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }
    }

    public static class MetadataConfig {
        private SyncConfig sync = new SyncConfig();

        public SyncConfig getSync() { return sync; }
        public void setSync(SyncConfig sync) { this.sync = sync; }
    }

    public static class SyncConfig {
        private Integer heartbeatIntervalMs = 5000;
        private Integer syncTimeoutMs = 30000;

        public Integer getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
        public void setHeartbeatIntervalMs(Integer heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }
        public Integer getSyncTimeoutMs() { return syncTimeoutMs; }
        public void setSyncTimeoutMs(Integer syncTimeoutMs) { this.syncTimeoutMs = syncTimeoutMs; }
    }
}