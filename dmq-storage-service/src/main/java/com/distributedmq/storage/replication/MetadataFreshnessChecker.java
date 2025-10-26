package com.distributedmq.storage.replication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Periodically checks if local metadata is stale and refreshes it if needed
 * Detects staleness by comparing local version with metadata service version
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "storage.metadata.freshness-check.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class MetadataFreshnessChecker {

    private final MetadataStore metadataStore;
    private final RestTemplate restTemplate;

    @Value("${storage.metadata.freshness-check.interval-ms:30000}")
    private long freshnessCheckInterval;

    @Value("${storage.metadata.freshness-check.staleness-tolerance-ms:60000}")
    private long stalenessToleranceMs;

    /**
     * Periodically check if local metadata is stale and refresh if needed
     * Runs every 30 seconds by default
     */
    @Scheduled(fixedDelayString = "${storage.metadata.freshness-check.interval-ms:30000}")
    public void checkMetadataFreshness() {
        try {
            String metadataServiceUrl = metadataStore.getMetadataServiceUrl();
            if (metadataServiceUrl == null) {
                log.debug("Metadata service URL not configured, skipping freshness check");
                return;
            }

            // Get current version from metadata service
            Long remoteVersion = fetchRemoteMetadataVersion(metadataServiceUrl);
            if (remoteVersion == null) {
                log.warn("Could not fetch remote metadata version, skipping freshness check");
                return;
            }

            Long localVersion = metadataStore.getCurrentMetadataVersion();
            Long lastUpdate = metadataStore.getLastMetadataUpdateTimestamp();
            long currentTime = System.currentTimeMillis();

            log.debug("Metadata version check: local={}, remote={}, lastUpdate={}, age={}ms",
                    localVersion, remoteVersion, lastUpdate, 
                    lastUpdate != null ? (currentTime - lastUpdate) : "unknown");

            // Check if local version is behind remote version
            if (remoteVersion > localVersion) {
                log.warn("⚠️ Local metadata is STALE! Local version: {}, Remote version: {}. Triggering refresh...",
                        localVersion, remoteVersion);
                pullFullMetadata(metadataServiceUrl);
                return;
            }

            // Check if metadata is too old (staleness by time)
            if (lastUpdate != null && (currentTime - lastUpdate) > stalenessToleranceMs) {
                log.warn("⚠️ Metadata age exceeds tolerance! Age: {}ms, Tolerance: {}ms. Triggering refresh...",
                        (currentTime - lastUpdate), stalenessToleranceMs);
                pullFullMetadata(metadataServiceUrl);
                return;
            }

            log.debug("✅ Metadata is fresh: version={}, age={}ms",
                    localVersion, lastUpdate != null ? (currentTime - lastUpdate) : "unknown");

        } catch (Exception e) {
            log.error("Error during metadata freshness check: {}", e.getMessage(), e);
        }
    }

    /**
     * Fetch current metadata version from metadata service
     */
    private Long fetchRemoteMetadataVersion(String metadataServiceUrl) {
        try {
            String endpoint = metadataServiceUrl + "/api/v1/metadata/version";
            log.debug("Fetching remote metadata version from: {}", endpoint);

            Map<String, Object> response = restTemplate.getForObject(endpoint, Map.class);
            if (response != null && response.containsKey("version")) {
                Long version = ((Number) response.get("version")).longValue();
                log.debug("Remote metadata version: {}", version);
                return version;
            }

            log.warn("Version endpoint returned unexpected response: {}", response);
            return null;

        } catch (Exception e) {
            log.error("Failed to fetch remote metadata version: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Pull full metadata from metadata service
     * Delegates to MetadataStore.pullInitialMetadata()
     */
    private void pullFullMetadata(String metadataServiceUrl) {
        try {
            log.info("Refreshing metadata due to staleness detection...");
            metadataStore.pullInitialMetadata();
            log.info("✅ Metadata refresh completed successfully");

        } catch (Exception e) {
            log.error("Failed to refresh metadata: {}", e.getMessage(), e);
        }
    }
}
