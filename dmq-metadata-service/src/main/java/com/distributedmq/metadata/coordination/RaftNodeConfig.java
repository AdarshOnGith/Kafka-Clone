package com.distributedmq.metadata.coordination;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Raft Node Configuration
 * Provides cluster topology and node information for Raft consensus
 */
@Configuration
@Getter
@ConfigurationProperties(prefix = "kraft.cluster")
public class RaftNodeConfig {

    @Value("${kraft.node-id}")
    private Integer nodeId;

    @Value("${server.port:8080}")
    private Integer port;

    @Value("${kraft.cluster.host:localhost}")
    private String host;

    private List<NodeConfig> nodes = new ArrayList<>();

    private List<NodeInfo> allNodes = new ArrayList<>();
    private List<NodeInfo> peers = new ArrayList<>();

    @PostConstruct
    public void init() {
        // Convert node configs to NodeInfo objects
        for (NodeConfig config : nodes) {
            NodeInfo nodeInfo = new NodeInfo(config.getId(), config.getHost(), config.getPort());
            allNodes.add(nodeInfo);

            // Peers are all nodes except this one
            if (config.getId() != nodeId) {
                peers.add(nodeInfo);
            }
        }

        // If no nodes configured, use default single-node setup
        if (allNodes.isEmpty()) {
            allNodes.add(new NodeInfo(nodeId, host, port));
        }

        // Log configuration
        System.out.println("Raft Node Configuration:");
        System.out.println("  Current Node: " + nodeId + " (" + host + ":" + port + ")");
        System.out.println("  All Nodes: " + allNodes.stream()
                .map(n -> n.getNodeId() + ":" + n.getHost() + ":" + n.getPort())
                .collect(Collectors.joining(", ")));
        System.out.println("  Peers: " + peers.stream()
                .map(n -> n.getNodeId() + ":" + n.getHost() + ":" + n.getPort())
                .collect(Collectors.joining(", ")));
    }

    /**
     * Get peer nodes (all nodes except this one)
     */
    public List<NodeInfo> getPeers() {
        return new ArrayList<>(peers);
    }

    /**
     * Get all nodes in the cluster
     */
    public List<NodeInfo> getAllNodes() {
        return new ArrayList<>(allNodes);
    }

    /**
     * Get current node info
     */
    public NodeInfo getCurrentNode() {
        return allNodes.stream()
                .filter(node -> node.getNodeId() == nodeId)
                .findFirst()
                .orElse(new NodeInfo(nodeId, host, port));
    }

    /**
     * Node configuration from YAML
     */
    public static class NodeConfig {
        private int id;
        private String host;
        private int port;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }

    public void setNodes(List<NodeConfig> nodes) {
        this.nodes = nodes;
    }

    /**
     * Node information
     */
    public static class NodeInfo {
        private final int nodeId;
        private final String host;
        private final int port;

        public NodeInfo(int nodeId, String host, int port) {
            this.nodeId = nodeId;
            this.host = host;
            this.port = port;
        }

        public int getNodeId() { return nodeId; }
        public String getHost() { return host; }
        public int getPort() { return port; }

        public String getAddress() {
            return host + ":" + port;
        }

        @Override
        public String toString() {
            return nodeId + ":" + host + ":" + port;
        }
    }
}
