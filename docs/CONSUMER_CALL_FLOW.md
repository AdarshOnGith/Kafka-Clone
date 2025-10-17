# Consumer Call Flow - How Consumer Knows Where to Call CES

## Overview
This document explains the complete flow from **User Application → Consumer Library → Consumer Egress Service (CES)** and how the consumer library knows **WHERE** to make the network calls.

---

## 🔍 The Short Answer

The consumer knows where to call CES because:
1. **User provides the URL** when creating the consumer (via `ConsumerConfig`)
2. **ConsumerConfig stores the URL** in `metadataServiceUrl` field
3. **DMQConsumer passes URL** to `ConsumerEgressClient` during initialization
4. **ConsumerEgressClient uses the URL** to build the full endpoint and make HTTP calls

---

## 📋 Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         USER APPLICATION                                 │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Step 1: Create Consumer with Config
                                    ▼
        ┌────────────────────────────────────────────────────┐
        │  ConsumerConfig config = ConsumerConfig.builder()  │
        │      .metadataServiceUrl("http://localhost:8080") │ ◄── USER PROVIDES CES URL
        │      .groupId("my-group")                          │
        │      .clientId("my-app")                           │
        │      .build();                                     │
        └────────────────────────────────────────────────────┘
                                    │
                                    │ Step 2: Create Consumer Instance
                                    ▼
        ┌────────────────────────────────────────────────────┐
        │  Consumer consumer = new DMQConsumer(config);      │
        └────────────────────────────────────────────────────┘
                                    │
                                    │
┌───────────────────────────────────▼─────────────────────────────────────┐
│                        CONSUMER LIBRARY (DMQConsumer)                    │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  public DMQConsumer(ConsumerConfig config) {                            │
│      this.config = config;  // Store config                             │
│                                                                          │
│      // Step 3: Extract CES URL from config                             │
│      String cesUrl = config.getMetadataServiceUrl(); ◄──────────────┐   │
│      //                      ^^^^^^^^^^^^^^^^^^^^^^^^^^^             │   │
│      //                      Returns: "http://localhost:8080"        │   │
│                                                                      │   │
│      // Step 4: Pass URL to ConsumerEgressClient                    │   │
│      this.egressClient = new ConsumerEgressClient(cesUrl); ─────────┘   │
│      //                                            ^^^^^^                │
│      //                                            URL is passed here    │
│  }                                                                       │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │
┌───────────────────────────────────▼─────────────────────────────────────┐
│                   CONSUMER EGRESS CLIENT (Network Layer)                │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  public ConsumerEgressClient(String metadataServiceUrl) {               │
│      this.egressServiceUrl = metadataServiceUrl; ◄─────────────────┐    │
│      //                                                             │    │
│      // Step 5: Store the URL                                      │    │
│      // egressServiceUrl = "http://localhost:8080"                 │    │
│                                                                     │    │
│      this.restTemplate = new RestTemplate(); // HTTP client        │    │
│  }                                                                  │    │
│                                                                     │    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ User calls: consumer.subscribe(topics)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        DMQConsumer.subscribe()                           │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  public void subscribe(Collection<String> topics) {                     │
│      // Build request                                                   │
│      ConsumerSubscriptionRequest request = ...                          │
│                                                                          │
│      // Step 6: Call egress client                                      │
│      ConsumerSubscriptionResponse response =                            │
│          egressClient.joinGroup(request); ──────────────────────┐       │
│      //  ^^^^^^^^^^^^ This has the CES URL stored!              │       │
│  }                                                               │       │
│                                                                  │       │
└──────────────────────────────────────────────────────────────────┘       │
                                                                           │
                                                                           │
┌──────────────────────────────────────────────────────────────────────────┘
│
│  Step 7: Build Full URL and Make HTTP Call
▼
┌─────────────────────────────────────────────────────────────────────────┐
│              ConsumerEgressClient.joinGroup()                            │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  public ConsumerSubscriptionResponse joinGroup(                         │
│          ConsumerSubscriptionRequest request) {                         │
│                                                                          │
│      // Step 8: Build full endpoint URL                                 │
│      String url = egressServiceUrl + "/api/consumer/join-group";        │
│      //            ^^^^^^^^^^^^^^^^   ^^^^^^^^^^^^^^^^^^^^^^^^          │
│      //            Base URL           Endpoint path                     │
│      //                                                                  │
│      //   Result: "http://localhost:8080/api/consumer/join-group"       │
│                                                                          │
│      // Step 9: Make HTTP POST request                                  │
│      HttpEntity<ConsumerSubscriptionRequest> entity =                   │
│          new HttpEntity<>(request, headers);                            │
│                                                                          │
│      ResponseEntity<ConsumerSubscriptionResponse> response =            │
│          restTemplate.exchange(                                         │
│              url,              ◄────── Full URL used here               │
│              HttpMethod.POST,                                           │
│              entity,                                                    │
│              ConsumerSubscriptionResponse.class                         │
│          );                                                             │
│                                                                          │
│      return response.getBody();                                         │
│  }                                                                       │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTP POST
                                    │ http://localhost:8080/api/consumer/join-group
                                    │ Body: ConsumerSubscriptionRequest JSON
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│         CONSUMER EGRESS SERVICE (CES) - Backend Server                  │
│         Running at: http://localhost:8080                               │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  @PostMapping("/api/consumer/join-group")                               │
│  public ConsumerSubscriptionResponse joinGroup(                         │
│          @RequestBody ConsumerSubscriptionRequest request) {            │
│                                                                          │
│      // Process request, create group, assign partitions                │
│      // Fetch partition metadata from storage nodes                     │
│      // Return response                                                 │
│                                                                          │
│      return ConsumerSubscriptionResponse.builder()                      │
│          .success(true)                                                 │
│          .partitions(partitionMetadata)                                 │
│          .build();                                                      │
│  }                                                                       │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTP Response
                                    │ Body: ConsumerSubscriptionResponse JSON
                                    ▼
              ┌──────────────────────────────────────┐
              │  Response flows back to consumer     │
              │  DMQConsumer processes response      │
              │  Stores partition metadata           │
              └──────────────────────────────────────┘
```

---

## 💻 Code Flow with Line-by-Line Explanation

### **Step 1: User Creates Consumer Config with CES URL**

```java
// User application code
ConsumerConfig config = ConsumerConfig.builder()
    .metadataServiceUrl("http://localhost:8080")  // ◄── USER SPECIFIES CES URL HERE!
    .groupId("checkout-service-group")
    .clientId("checkout-service")
    .autoOffsetReset("earliest")
    .build();
```

**Key Point:** The `metadataServiceUrl` is where CES is running. This could be:
- `http://localhost:8080` (local development)
- `http://ces-service:8080` (Docker/Kubernetes)
- `https://ces.example.com` (production)

---

### **Step 2: User Creates Consumer Instance**

```java
// User creates consumer by passing config
Consumer consumer = new DMQConsumer(config);
//                                  ^^^^^^
//                                  Config contains CES URL
```

---

### **Step 3: DMQConsumer Constructor Extracts URL**

```java
// DMQConsumer.java (Constructor)
public DMQConsumer(ConsumerConfig config) {
    this.config = config;
    
    // Extract the CES URL from config
    String cesUrl = config.getMetadataServiceUrl();
    //              ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    //              Returns: "http://localhost:8080"
    
    // Pass URL to ConsumerEgressClient
    this.egressClient = new ConsumerEgressClient(cesUrl);
    //                                            ^^^^^^
    //                                            URL is passed here!
    
    log.info("DMQConsumer initialized: consumerId={}, groupId={}", consumerId, config.getGroupId());
}
```

**Key Point:** The consumer extracts the CES URL and passes it to the network client.

---

### **Step 4: ConsumerEgressClient Stores the URL**

```java
// ConsumerEgressClient.java (Constructor)
public ConsumerEgressClient(String metadataServiceUrl) {
    // Store the URL in instance variable
    this.egressServiceUrl = metadataServiceUrl;
    //                      ^^^^^^^^^^^^^^^^^^
    //                      "http://localhost:8080" is stored here
    
    // Create HTTP client (RestTemplate from Spring)
    this.restTemplate = new RestTemplate();
    
    log.info("ConsumerEgressClient initialized with egress service URL: {}", egressServiceUrl);
}
```

**Key Point:** The URL is stored as an instance variable and will be used for all network calls.

---

### **Step 5: User Calls subscribe() Method**

```java
// User application code
List<String> topics = Arrays.asList("orders", "payments");
consumer.subscribe(topics);
//       ^^^^^^^^^
//       This triggers the network call!
```

---

### **Step 6: DMQConsumer Calls ConsumerEgressClient**

```java
// DMQConsumer.java (subscribe method)
@Override
public void subscribe(Collection<String> topics) {
    // Build request with topics, groupId, consumerId
    ConsumerSubscriptionRequest request = ConsumerSubscriptionRequest.builder()
        .groupId(config.getGroupId())
        .consumerId(consumerId)
        .topics(new ArrayList<>(topics))
        .clientId(config.getClientId())
        .build();
    
    // Call egress client to join group
    ConsumerSubscriptionResponse response = egressClient.joinGroup(request);
    //                                      ^^^^^^^^^^^^
    //                                      This object has the CES URL stored!
    
    // Process response...
}
```

---

### **Step 7: ConsumerEgressClient Builds Full URL and Makes HTTP Call**

```java
// ConsumerEgressClient.java (joinGroup method)
public ConsumerSubscriptionResponse joinGroup(ConsumerSubscriptionRequest request) {
    // Build the full endpoint URL
    String url = egressServiceUrl + "/api/consumer/join-group";
    //           ^^^^^^^^^^^^^^^^   ^^^^^^^^^^^^^^^^^^^^^^^^
    //           Base URL           Endpoint path
    //           "http://localhost:8080" + "/api/consumer/join-group"
    //           = "http://localhost:8080/api/consumer/join-group"
    
    log.info("Joining consumer group: group={}, consumerId={}, topics={}", 
        request.getGroupId(), request.getConsumerId(), request.getTopics());
    
    try {
        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Create HTTP entity with request body
        HttpEntity<ConsumerSubscriptionRequest> entity = new HttpEntity<>(request, headers);
        
        // Make HTTP POST request using RestTemplate
        ResponseEntity<ConsumerSubscriptionResponse> response = restTemplate.exchange(
            url,                                      // ◄── Full URL
            HttpMethod.POST,                          // HTTP method
            entity,                                   // Request body + headers
            ConsumerSubscriptionResponse.class        // Expected response type
        );
        
        // Return response body
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return response.getBody();
        }
        
    } catch (RestClientException e) {
        log.error("Failed to join group: {}", e.getMessage(), e);
        return ConsumerSubscriptionResponse.builder()
            .success(false)
            .errorMessage("Network error: " + e.getMessage())
            .build();
    }
}
```

**Key Point:** The `egressServiceUrl` stored during initialization is combined with the endpoint path to create the full URL for the HTTP call.

---

## 🔗 URL Configuration Flow

### **Development Environment:**
```java
ConsumerConfig config = ConsumerConfig.builder()
    .metadataServiceUrl("http://localhost:8080")
    .build();

// Calls to: http://localhost:8080/api/consumer/join-group
```

### **Docker Environment:**
```java
ConsumerConfig config = ConsumerConfig.builder()
    .metadataServiceUrl("http://ces-service:8080")  // Docker service name
    .build();

// Calls to: http://ces-service:8080/api/consumer/join-group
```

### **Production Environment:**
```java
ConsumerConfig config = ConsumerConfig.builder()
    .metadataServiceUrl("https://ces.mycompany.com")
    .build();

// Calls to: https://ces.mycompany.com/api/consumer/join-group
```

### **Environment Variable (Best Practice):**
```java
String cesUrl = System.getenv("CES_URL");  // From environment variable
if (cesUrl == null) {
    cesUrl = "http://localhost:8080";  // Default fallback
}

ConsumerConfig config = ConsumerConfig.builder()
    .metadataServiceUrl(cesUrl)
    .build();
```

---

## 🌐 HTTP Request Details

### **Request:**
```
POST http://localhost:8080/api/consumer/join-group
Content-Type: application/json

{
  "groupId": "checkout-service-group",
  "consumerId": "checkout-service-abc123",
  "topics": ["orders", "payments"],
  "clientId": "checkout-service"
}
```

### **Response:**
```json
{
  "success": true,
  "groupId": "checkout-service-group",
  "consumerId": "checkout-service-abc123",
  "generationId": 1,
  "partitions": [
    {
      "topicName": "orders",
      "partitionId": 0,
      "leader": {
        "id": 1,
        "host": "localhost",
        "port": 9092
      },
      "currentOffset": 0,
      "highWaterMark": 100
    }
  ]
}
```

---

## 📊 Data Flow Summary

| Step | Component | Action | Data |
|------|-----------|--------|------|
| 1 | User App | Creates config | `metadataServiceUrl = "http://localhost:8080"` |
| 2 | User App | Creates consumer | Passes config to constructor |
| 3 | DMQConsumer | Constructor | Extracts URL from config |
| 4 | DMQConsumer | Constructor | Passes URL to `ConsumerEgressClient` |
| 5 | ConsumerEgressClient | Constructor | Stores URL in `egressServiceUrl` |
| 6 | User App | Calls `subscribe()` | Passes topic list |
| 7 | DMQConsumer | `subscribe()` | Builds request, calls `egressClient.joinGroup()` |
| 8 | ConsumerEgressClient | `joinGroup()` | Builds full URL: `egressServiceUrl + "/api/consumer/join-group"` |
| 9 | ConsumerEgressClient | `joinGroup()` | Makes HTTP POST to CES |
| 10 | CES | REST Controller | Receives request, processes, returns response |
| 11 | ConsumerEgressClient | `joinGroup()` | Receives response, returns to DMQConsumer |
| 12 | DMQConsumer | `subscribe()` | Processes response, stores partition metadata |

---

## 🔑 Key Takeaways

1. **User provides the CES URL** via `ConsumerConfig.metadataServiceUrl`
2. **URL flows through layers:**
   - ConsumerConfig → DMQConsumer → ConsumerEgressClient
3. **ConsumerEgressClient stores the URL** and uses it for all network calls
4. **Full endpoint URL is built** by concatenating base URL + endpoint path
5. **RestTemplate makes the HTTP call** to the CES server
6. **Response flows back** through the same layers

---

## 🧪 Testing the Flow

To verify the flow is working:

```java
// Enable debug logging to see the URLs being called
log.info("ConsumerEgressClient initialized with egress service URL: {}", egressServiceUrl);
log.info("Joining consumer group: group={}, consumerId={}, topics={}", ...);
log.info("Successfully joined group: group={}, partitions={}", ...);
```

You'll see logs like:
```
ConsumerEgressClient initialized with egress service URL: http://localhost:8080
Joining consumer group: group=my-group, consumerId=my-consumer-abc123, topics=[orders, payments]
Successfully joined group: group=my-group, partitions=3
```

This confirms the URL is being passed correctly and the HTTP call is working! 🎯
