package com.distributedmq.client.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mock Metadata Service
 * Reads cluster metadata in your friend's format and converts it to our producer's expected format
 * 
 * Your Friend's Format:
 * {
 *   "brokers": [...],
 *   "partitions": [...],
 *   "timestamp": 123
 * }
 * 
 * Converted To Producer Format:
 * {
 *   "topicName": "orders",
 *   "partitionCount": 2,
 *   "partitions": [...]
 * }
 */
public class MockMetadataServer {

    private static HttpServer server;
    private static Map<String, Object> clusterMetadata;

    public static void main(String[] args) {
        try {
            System.out.println("╔═══════════════════════════════════════════════════════╗");
            System.out.println("║        Mock Metadata Service (Port 8081)             ║");
            System.out.println("╚═══════════════════════════════════════════════════════╝");
            System.out.println();

            // Load cluster metadata from JSON file
            loadClusterMetadata();

            // Start mock metadata server
            startServer(8081);

            System.out.println("✅ Mock Metadata Service started on http://localhost:8081");
            System.out.println();
            System.out.println("Available endpoints:");
            System.out.println("  GET /api/v1/metadata/topics/{topicName}");
            System.out.println();
            System.out.println("Loaded cluster data:");
            System.out.println("  - Brokers: " + ((List) clusterMetadata.get("brokers")).size());
            System.out.println("  - Partitions: " + ((List) clusterMetadata.get("partitions")).size());
            System.out.println();
            System.out.println("Topics available:");
            Set<String> topics = getAvailableTopics();
            topics.forEach(topic -> System.out.println("  - " + topic));
            System.out.println();
            System.out.println("Press Ctrl+C to stop...");
            System.out.println("═══════════════════════════════════════════════════════");

            // Keep server running
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("❌ Error starting mock metadata service: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Load cluster metadata from JSON file
     */
    @SuppressWarnings("unchecked")
    private static void loadClusterMetadata() {
        try {
            InputStream is = MockMetadataServer.class.getResourceAsStream("/cluster-metadata.json");
            if (is == null) {
                throw new RuntimeException("cluster-metadata.json not found in resources");
            }

            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            clusterMetadata = parseJson(json);

            System.out.println("📂 Loaded cluster metadata from cluster-metadata.json");

        } catch (Exception e) {
            throw new RuntimeException("Failed to load cluster metadata", e);
        }
    }

    /**
     * Start HTTP server
     */
    private static void startServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/v1/metadata/topics", MockMetadataServer::handleMetadataRequest);
        server.setExecutor(null);
        server.start();
    }

    /**
     * Handle metadata request - convert friend's format to our format
     */
    @SuppressWarnings("unchecked")
    private static void handleMetadataRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String topicName = path.substring(path.lastIndexOf('/') + 1);

        System.out.println("📨 [REQUEST] GET " + path);
        System.out.println("   Topic requested: " + topicName);

        if (exchange.getRequestMethod().equals("GET")) {
            try {
                // Get partitions for this topic from cluster metadata
                List<Map<String, Object>> allPartitions = (List<Map<String, Object>>) clusterMetadata.get("partitions");
                List<Map<String, Object>> topicPartitions = allPartitions.stream()
                        .filter(p -> topicName.equals(p.get("topic")))
                        .collect(Collectors.toList());

                if (topicPartitions.isEmpty()) {
                    System.out.println("   ❌ Topic not found: " + topicName);
                    sendError(exchange, 404, "Topic not found: " + topicName);
                    return;
                }

                // Convert to producer's expected format
                String response = convertToProducerFormat(topicName, topicPartitions);

                System.out.println("   ✅ Returning metadata for topic '" + topicName + "'");
                System.out.println("      Partitions: " + topicPartitions.size());

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();

            } catch (Exception e) {
                System.err.println("   ❌ Error processing request: " + e.getMessage());
                sendError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        } else {
            sendError(exchange, 405, "Method not allowed");
        }
    }

    /**
     * Convert friend's format to producer's expected format
     */
    @SuppressWarnings("unchecked")
    private static String convertToProducerFormat(String topicName, List<Map<String, Object>> topicPartitions) {
        List<Map<String, Object>> brokers = (List<Map<String, Object>>) clusterMetadata.get("brokers");
        Map<Integer, Map<String, Object>> brokerMap = new HashMap<>();
        
        // Create broker lookup map
        for (Map<String, Object> broker : brokers) {
            brokerMap.put((Integer) broker.get("id"), broker);
        }

        // Build partitions array
        StringBuilder partitionsJson = new StringBuilder();
        partitionsJson.append("[");
        
        for (int i = 0; i < topicPartitions.size(); i++) {
            Map<String, Object> partition = topicPartitions.get(i);
            Integer partitionId = (Integer) partition.get("partition");
            Integer leaderId = (Integer) partition.get("leaderId");
            Map<String, Object> leaderBroker = brokerMap.get(leaderId);

            if (i > 0) partitionsJson.append(",");
            
            String host = (String) leaderBroker.get("host");
            Integer port = (Integer) leaderBroker.get("port");
            
            // Build address with proper protocol
            String address;
            if (host.contains("devtunnels.ms") || host.contains("ngrok") || host.contains("cloudflare")) {
                // Dev tunnel / tunnel services - use HTTPS
                address = "https://" + host;
            } else if (port == 443) {
                // Standard HTTPS port
                address = "https://" + host;
            } else if (port == 80) {
                // Standard HTTP port
                address = "http://" + host;
            } else {
                // Custom port - use HTTP with port
                address = "http://" + host + ":" + port;
            }
            
            partitionsJson.append("{")
                    .append("\"topicName\":\"").append(topicName).append("\",")
                    .append("\"partitionId\":").append(partitionId).append(",")
                    .append("\"leader\":{")
                    .append("\"brokerId\":").append(leaderId).append(",")
                    .append("\"host\":\"").append(host).append("\",")
                    .append("\"port\":").append(port).append(",")
                    .append("\"address\":\"").append(address).append("\",")
                    .append("\"status\":\"ONLINE\"")
                    .append("}")
                    .append("}");
        }
        
        partitionsJson.append("]");

        // Build complete response
        return "{"
                + "\"topicName\":\"" + topicName + "\","
                + "\"partitionCount\":" + topicPartitions.size() + ","
                + "\"replicationFactor\":3,"
                + "\"partitions\":" + partitionsJson.toString()
                + "}";
    }

    /**
     * Get available topics from metadata
     */
    @SuppressWarnings("unchecked")
    private static Set<String> getAvailableTopics() {
        List<Map<String, Object>> partitions = (List<Map<String, Object>>) clusterMetadata.get("partitions");
        return partitions.stream()
                .map(p -> (String) p.get("topic"))
                .collect(Collectors.toSet());
    }

    /**
     * Send error response
     */
    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String response = "{\"error\":\"" + message + "\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    /**
     * Parse JSON using Jackson
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }
}
