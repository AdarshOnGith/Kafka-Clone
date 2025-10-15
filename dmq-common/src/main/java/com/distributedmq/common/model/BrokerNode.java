package com.distributedmq.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Represents a broker/storage node in the cluster
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokerNode implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer brokerId;
    private String host;
    private Integer port;
    private String rack;
    private BrokerStatus status;

    public String getAddress() {
        return host + ":" + port;
    }
}
