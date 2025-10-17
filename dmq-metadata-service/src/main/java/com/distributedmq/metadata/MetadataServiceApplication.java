package com.distributedmq.metadata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Metadata Service Application
 * Handles metadata management and cluster coordination
 * 
 * Note: Database auto-configuration disabled for Phase 1 testing
 */
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
@EnableScheduling
public class MetadataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetadataServiceApplication.class, args);
    }
}
