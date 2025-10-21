package com.distributedmq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to produce messages (supports batching)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProduceRequest {
    private String topic;
    private Integer partition;
    private List<ProduceMessage> messages; // Batch support
    
    // Producer identification for idempotent/transactional producers
    private String producerId;
    private Integer producerEpoch;
    
    // Acknowledgment requirements
    private Integer requiredAcks; // 0, 1, or -1 (all)
    private Long timeoutMs;

    // Leader HWM for lag calculation (sent by leader during replication)
    private Long leaderHighWaterMark;

    // TODO: Add transactional support
    // TODO: Add compression type
    
    /**
     * Individual message in batch
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProduceMessage {
        private String key;
        private byte[] value;
        private Long timestamp; // Optional, will use current time if null
    }
}
