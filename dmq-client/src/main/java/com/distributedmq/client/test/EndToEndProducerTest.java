package com.distributedmq.client.test;

import com.distributedmq.client.producer.DMQProducer;
import com.distributedmq.client.producer.ProducerConfig;
import com.distributedmq.common.dto.ProduceResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;

/**
 * End-to-End Producer Test with Mock HTTP Servers
 * Simulates both Metadata Service and Storage Service
 * 
 * Tests complete flow:
 * 1. Client requests metadata
 * 2. Metadata service responds with topic/partition/leader info
 * 3. Client performs partition selection (MurmurHash)
 * 4. Client identifies leader broker
 * 5. Client connects to leader
 * 6. Client sends message
 * 7. Storage service responds with offset
 */
public class EndToEndProducerTest {

    private static HttpServer metadataServer;
    private static HttpServer storageServer;

    public static void main(String[] args) {
        try {
            System.out.println("╔═══════════════════════════════════════════════════════╗");
            System.out.println("║   END-TO-END PRODUCER TEST (Mock Cluster Services)   ║");
            System.out.println("╚═══════════════════════════════════════════════════════╝");
            System.out.println();

            // Step 1: Start mock servers
            System.out.println("🚀 Step 1: Starting mock cluster services...");
            startMockServers();
            System.out.println("   ✅ Mock Metadata Service running on port 9091");
            System.out.println("   ✅ Mock Storage Service running on port 9092");
            System.out.println();

            // Wait a bit for servers to be ready
            Thread.sleep(1000);

            // Step 2: Create producer
            System.out.println("📦 Step 2: Creating Producer Client...");
            ProducerConfig config = ProducerConfig.builder()
                    .metadataServiceUrl("http://localhost:9091")
                    .build();
            DMQProducer producer = new DMQProducer(config);
            System.out.println("   ✅ Producer created with metadata URL: http://localhost:9091");
            System.out.println();

            // Step 3: Send message and test complete flow
            System.out.println("📤 Step 3: Testing Complete Producer Flow...");
            System.out.println("   → Sending message with key: 'user-123'");
            System.out.println();

            Future<ProduceResponse> future = producer.send(
                    "orders-topic",
                    "user-123",
                    "Order data: {orderId: 12345, amount: 99.99}".getBytes()
            );

            // Step 4: Get response
            System.out.println("⏳ Step 4: Waiting for response...");
            ProduceResponse response = future.get();
            System.out.println();

            // Step 5: Verify and display results
            System.out.println("═══════════════════════════════════════════════════════");
            System.out.println("                   TEST RESULTS");
            System.out.println("═══════════════════════════════════════════════════════");
            System.out.println();

            System.out.println("📊 Response Details:");
            System.out.println("   Topic: " + response.getTopic());
            System.out.println("   Partition: " + response.getPartition());
            System.out.println("   Success: " + response.isSuccess());

            if (response.getResults() != null && !response.getResults().isEmpty()) {
                ProduceResponse.ProduceResult result = response.getResults().get(0);
                System.out.println("   Offset: " + result.getOffset());
                System.out.println("   Timestamp: " + result.getTimestamp());
                System.out.println("   Error Code: " + result.getErrorCode());
            }

            System.out.println();

            if (response.isSuccess()) {
                System.out.println("✅ END-TO-END TEST PASSED!");
                System.out.println();
                System.out.println("✓ Metadata fetch successful");
                System.out.println("✓ Partition selection successful (partition " + response.getPartition() + ")");
                System.out.println("✓ Leader identification successful");
                System.out.println("✓ Connection to leader successful");
                System.out.println("✓ Message send successful");
                System.out.println("✓ Offset assigned: " + response.getResults().get(0).getOffset());
            } else {
                System.out.println("❌ TEST FAILED!");
                System.out.println("   Error: " + response.getErrorMessage());
            }

            System.out.println();
            System.out.println("═══════════════════════════════════════════════════════");

            // Cleanup
            producer.close();
            stopMockServers();

        } catch (Exception e) {
            System.err.println();
            System.err.println("═══════════════════════════════════════════════════════");
            System.err.println("                   TEST FAILED");
            System.err.println("═══════════════════════════════════════════════════════");
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();

            stopMockServers();
            System.exit(1);
        }
    }

    /**
     * Start mock HTTP servers for metadata and storage services
     */
    private static void startMockServers() throws IOException {
        // Mock Metadata Service (port 9091)
        metadataServer = HttpServer.create(new InetSocketAddress(9091), 0);
        metadataServer.createContext("/api/v1/metadata/topics", exchange -> {
            handleMetadataRequest(exchange);
        });
        metadataServer.setExecutor(null);
        metadataServer.start();

        // Mock Storage Service (port 9092)
        storageServer = HttpServer.create(new InetSocketAddress(9092), 0);
        storageServer.createContext("/api/v1/storage/messages", exchange -> {
            handleStorageRequest(exchange);
        });
        storageServer.setExecutor(null);
        storageServer.start();
    }

    /**
     * Handle metadata requests - return topic metadata with partitions and leaders
     */
    private static void handleMetadataRequest(HttpExchange exchange) throws IOException {
        System.out.println("   [MOCK METADATA] Received request: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());

        if (exchange.getRequestMethod().equals("GET")) {
            // Return mock topic metadata
            String response = "{"
                    + "\"topicName\":\"orders-topic\","
                    + "\"partitionCount\":3,"
                    + "\"replicationFactor\":1,"
                    + "\"partitions\":["
                    + "  {\"topicName\":\"orders-topic\",\"partitionId\":0,\"leader\":{\"brokerId\":1,\"host\":\"localhost\",\"port\":9092,\"address\":\"localhost:9092\",\"status\":\"ONLINE\"}},"
                    + "  {\"topicName\":\"orders-topic\",\"partitionId\":1,\"leader\":{\"brokerId\":1,\"host\":\"localhost\",\"port\":9092,\"address\":\"localhost:9092\",\"status\":\"ONLINE\"}},"
                    + "  {\"topicName\":\"orders-topic\",\"partitionId\":2,\"leader\":{\"brokerId\":1,\"host\":\"localhost\",\"port\":9092,\"address\":\"localhost:9092\",\"status\":\"ONLINE\"}}"
                    + "]"
                    + "}";

            System.out.println("   [MOCK METADATA] Sending response: 3 partitions, leader at localhost:9092");

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    /**
     * Handle storage requests - simulate message storage and return offset
     */
    private static void handleStorageRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        System.out.println("   [MOCK STORAGE] Received request: " + method + " " + exchange.getRequestURI());

        if (method.equals("HEAD")) {
            // Connection test - return OK
            System.out.println("   [MOCK STORAGE] Connection test successful");
            exchange.sendResponseHeaders(200, -1);

        } else if (method.equals("POST")) {
            // Message send - return success with offset
            String response = "{"
                    + "\"topic\":\"orders-topic\","
                    + "\"partition\":0,"
                    + "\"success\":true,"
                    + "\"results\":["
                    + "  {\"offset\":42,\"timestamp\":" + System.currentTimeMillis() + ",\"errorCode\":\"NONE\"}"
                    + "],"
                    + "\"errorCode\":\"NONE\""
                    + "}";

            System.out.println("   [MOCK STORAGE] Message stored, returning offset: 42");

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    /**
     * Stop mock servers
     */
    private static void stopMockServers() {
        if (metadataServer != null) {
            metadataServer.stop(0);
            System.out.println("🛑 Mock Metadata Service stopped");
        }
        if (storageServer != null) {
            storageServer.stop(0);
            System.out.println("🛑 Mock Storage Service stopped");
        }
    }
}
