package com.distributedmq.storage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for storage service.
 */
@Configuration
public class AsyncConfig {
    
    @Bean(name = "storageTaskExecutor")
    public Executor storageTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("storage-async-");
        executor.initialize();
        return executor;
    }
    
    @Bean(name = "replicationExecutor")
    public Executor replicationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("replication-");
        executor.initialize();
        return executor;
    }
}
