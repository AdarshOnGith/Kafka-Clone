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
        return metadataStore;
    }
}