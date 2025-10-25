package com.distributedmq.metadata.coordination;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for a Raft cluster node
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaftNodeConfig {
    
    private Integer id;
    private String host;
    private Integer port;
    
    public String getAddress() {
        return host + ":" + port;
    }
}
