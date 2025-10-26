package com.distributedmq.storage.config;

import com.distributedmq.storage.heartbeat.HeartbeatSender;
import com.distributedmq.storage.replication.MetadataStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for storage service components
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class StorageServiceConfig {

    private final StorageConfig storageConfig;
    private final ClusterTopologyConfig clusterTopologyConfig;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public MetadataStore metadataStore() {
        MetadataStore metadataStore = new MetadataStore();
        
        Integer brokerId = storageConfig.getBroker().getId();
        metadataStore.setLocalBrokerId(brokerId);
        
        // Get broker configuration from config/services.json
        ClusterTopologyConfig.StorageServiceInfo brokerInfo = 
            clusterTopologyConfig.getBrokerById(brokerId);
        
        if (brokerInfo != null) {
            // Use configuration from services.json
            metadataStore.setLocalBrokerHost(brokerInfo.getHost());
            metadataStore.setLocalBrokerPort(brokerInfo.getPort());
            log.info("Loaded broker {} configuration from services.json: {}:{}", 
                brokerId, brokerInfo.getHost(), brokerInfo.getPort());
        } else {
            // Fallback to StorageConfig values
            log.warn("Broker {} not found in services.json, using fallback configuration", brokerId);
            metadataStore.setLocalBrokerHost(storageConfig.getBroker().getHost());
            metadataStore.setLocalBrokerPort(storageConfig.getBroker().getPort());
        }

        // Get metadata service URL from config/services.json (use primary metadata service)
        ClusterTopologyConfig.MetadataServiceInfo metadataService = 
            clusterTopologyConfig.getPrimaryMetadataService();
        
        String metadataServiceUrl;
        if (metadataService != null && metadataService.getUrl() != null) {
            metadataServiceUrl = metadataService.getUrl();
            log.info("Using metadata service from services.json: {}", metadataServiceUrl);
        } else {
            // Fallback to hardcoded URL
            metadataServiceUrl = "http://localhost:9091";
            log.warn("Metadata service not found in services.json, using fallback: {}", metadataServiceUrl);
        }
        
        metadataStore.setMetadataServiceUrl(metadataServiceUrl);

        // Register this broker with the metadata service
        metadataStore.registerWithMetadataService();

        // Pull initial metadata after registration
        metadataStore.pullInitialMetadata();

        return metadataStore;
    }

    /**
     * Heartbeat Sender Bean
     * Automatically starts sending heartbeats to metadata service every 5 seconds
     * Also performs metadata version checking and periodic refresh
     */
    @Bean
    public HeartbeatSender heartbeatSender(RestTemplate restTemplate, MetadataStore metadataStore) {
        Integer brokerId = storageConfig.getBroker().getId();
        
        // Get metadata service URL from config/services.json
        ClusterTopologyConfig.MetadataServiceInfo metadataService = 
            clusterTopologyConfig.getPrimaryMetadataService();
        
        String metadataServiceUrl;
        if (metadataService != null && metadataService.getUrl() != null) {
            metadataServiceUrl = metadataService.getUrl();
        } else {
            metadataServiceUrl = "http://localhost:9091";
            log.warn("Using fallback metadata service URL for heartbeat: {}", metadataServiceUrl);
        }
        
        HeartbeatSender sender = new HeartbeatSender(brokerId, metadataServiceUrl, restTemplate, metadataStore);
        log.info("Heartbeat sender configured for broker {} → {}", brokerId, metadataServiceUrl);
        
        return sender;
    }
}