package com.distributedmq.storage.service;

import com.distributedmq.common.dto.ConsumeRequest;
import com.distributedmq.common.dto.ConsumeResponse;
import com.distributedmq.common.dto.ProduceRequest;
import com.distributedmq.common.dto.ProduceResponse;

/**
 * Service interface for Storage operations
 */
public interface StorageService {

    /**
     * Append message to partition
     */
    ProduceResponse append(ProduceRequest request);

    /**
     * Fetch messages from partition
     */
    ConsumeResponse fetch(ConsumeRequest request);

    /**
     * Get high water mark for partition
     */
    Long getHighWaterMark(String topic, Integer partition);

    /**
     * Flush all pending writes
     */
    void flush();

    // TODO: Add replication methods
    // TODO: Add partition creation/deletion methods
}
