package com.distributedmq.metadata.coordination;

import lombok.Builder;
import lombok.Data;

/**
 * Information about a registered broker
 */
@Data
@Builder
public class BrokerInfo {
    private final int brokerId;
    private final String host;
    private final int port;
    private final long registrationTime;
}