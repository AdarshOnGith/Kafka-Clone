package com.distributedmq.storage.config;

import com.distributedmq.storage.replication.MetadataStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for storage service components
 */
@Configuration
@RequiredArgsConstructor
public class StorageServiceConfig {

    private final StorageConfig storageConfig;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public MetadataStore metadataStore() {
        MetadataStore metadataStore = new MetadataStore();
        metadataStore.setLocalBrokerId(storageConfig.getBroker().getId());

        // Set the paired metadata service URL
        // For now, hardcode to localhost:9091 since ServiceDiscovery has issues
        String metadataServiceUrl = "http://localhost:9091";
        metadataStore.setMetadataServiceUrl(metadataServiceUrl);

        // Register this broker with the metadata service
        metadataStore.registerWithMetadataService();

        return metadataStore;
    }
}