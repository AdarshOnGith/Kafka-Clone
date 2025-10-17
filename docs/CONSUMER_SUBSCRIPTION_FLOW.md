# Consumer Subscription Flow - Request & Response Details

## Overview
When a consumer subscribes to a **group of topics**, it sends a single request containing all topics to the Consumer Egress Service (CES). This document explains the request flow, responses, and error scenarios.

---

## 1. Request Flow

### **Step 1: Consumer calls `subscribe()`**
```java
// Consumer code
List<String> topics = Arrays.asList("orders", "payments", "inventory");
consumer.subscribe(topics);
```

### **Step 2: Build `ConsumerSubscriptionRequest`**
The DMQConsumer builds a single request containing ALL topics:

```java
ConsumerSubscriptionRequest request = ConsumerSubscriptionRequest.builder()
    .groupId("checkout-service-group")           // Consumer group ID
    .consumerId("checkout-service-abc123")       // Unique consumer ID
    .topics(Arrays.asList("orders", "payments", "inventory"))  // ALL topics in ONE request
    .clientId("checkout-service")
    .build();
```

**Request JSON sent to CES:**
```json
{
  "groupId": "checkout-service-group",
  "consumerId": "checkout-service-abc123",
  "topics": ["orders", "payments", "inventory"],
  "clientId": "checkout-service"
}
```

### **Step 3: Send HTTP POST to Consumer Egress Service**
```
POST http://localhost:8080/api/consumer/join-group
Content-Type: application/json

{
  "groupId": "checkout-service-group",
  "consumerId": "checkout-service-abc123",
  "topics": ["orders", "payments", "inventory"],
  "clientId": "checkout-service"
}
```

---

## 2. Consumer Egress Service Processing

### **CES Responsibilities:**
1. **Validate Request** - Check if topics exist
2. **Create/Join Consumer Group** - Create group if doesn't exist
3. **Assign Partitions** - Assign partitions from all topics to this consumer
4. **Fetch Metadata** - Get partition metadata (leader, offsets, ISR) from storage nodes
5. **Return Response** - Send partition metadata back to consumer

### **CES Logic (Pseudocode):**
```java
@PostMapping("/api/consumer/join-group")
public ConsumerSubscriptionResponse joinGroup(@RequestBody ConsumerSubscriptionRequest request) {
    
    // 1. Validate topics exist
    List<String> invalidTopics = new ArrayList<>();
    List<PartitionMetadata> allPartitions = new ArrayList<>();
    
    for (String topic : request.getTopics()) {
        if (!topicExists(topic)) {
            invalidTopics.add(topic);
        } else {
            // 2. Get partitions for this topic
            List<PartitionMetadata> topicPartitions = getPartitionsForTopic(topic);
            allPartitions.addAll(topicPartitions);
        }
    }
    
    // 3. Check if ALL topics were invalid
    if (invalidTopics.size() == request.getTopics().size()) {
        // All topics invalid - FAIL
        return ConsumerSubscriptionResponse.builder()
            .success(false)
            .errorMessage("All topics do not exist: " + invalidTopics)
            .build();
    }
    
    // 4. Check if SOME topics were invalid
    if (!invalidTopics.isEmpty()) {
        // Some topics invalid - PARTIAL SUCCESS (depends on config)
        // Option A: Return error
        return ConsumerSubscriptionResponse.builder()
            .success(false)
            .errorMessage("Some topics do not exist: " + invalidTopics)
            .build();
        
        // Option B: Return only valid partitions with warning (Future)
        // return partitions for valid topics + warning
    }
    
    // 5. All topics valid - Create/join group and assign partitions
    ConsumerGroup group = createOrJoinGroup(request.getGroupId());
    group.addMember(request.getConsumerId(), allPartitions);
    
    // 6. Return success with ALL partitions
    return ConsumerSubscriptionResponse.builder()
        .success(true)
        .groupId(request.getGroupId())
        .consumerId(request.getConsumerId())
        .partitions(allPartitions)  // Partitions from ALL valid topics
        .generationId(group.getGenerationId())
        .build();
}
```

---

## 3. Response Scenarios

### **Scenario A: ALL Topics Exist ✅**

**Request:**
```json
{
  "groupId": "checkout-service-group",
  "consumerId": "checkout-service-abc123",
  "topics": ["orders", "payments", "inventory"]
}
```

**Response:**
```json
{
  "success": true,
  "errorMessage": null,
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
      "highWaterMark": 100,
      "isr": [1, 2, 3]
    },
    {
      "topicName": "orders",
      "partitionId": 1,
      "leader": {
        "id": 2,
        "host": "localhost",
        "port": 9093
      },
      "currentOffset": 0,
      "highWaterMark": 150,
      "isr": [2, 3, 1]
    },
    {
      "topicName": "payments",
      "partitionId": 0,
      "leader": {
        "id": 1,
        "host": "localhost",
        "port": 9092
      },
      "currentOffset": 0,
      "highWaterMark": 50,
      "isr": [1, 2]
    },
    {
      "topicName": "inventory",
      "partitionId": 0,
      "leader": {
        "id": 3,
        "host": "localhost",
        "port": 9094
      },
      "currentOffset": 0,
      "highWaterMark": 75,
      "isr": [3, 1, 2]
    }
  ]
}
```

**Consumer State After Subscribe:**
```
✅ Subscribed to 3 topics
✅ Assigned 4 partitions total
   - orders-0 (leader: broker-1)
   - orders-1 (leader: broker-2)
   - payments-0 (leader: broker-1)
   - inventory-0 (leader: broker-3)
```

---

### **Scenario B: SOME Topics Don't Exist ⚠️**

**Request:**
```json
{
  "groupId": "checkout-service-group",
  "consumerId": "checkout-service-abc123",
  "topics": ["orders", "payments", "non-existent-topic"]
}
```

**Response (Current Implementation - FAIL):**
```json
{
  "success": false,
  "errorMessage": "Some topics do not exist: [non-existent-topic]",
  "groupId": null,
  "consumerId": null,
  "partitions": null
}
```

**Consumer Behavior:**
```java
// Consumer throws RuntimeException
throw new RuntimeException("Failed to join consumer group: Some topics do not exist: [non-existent-topic]");
```

**Alternative Response (Future - PARTIAL SUCCESS):**
```json
{
  "success": true,
  "errorMessage": "Warning: Some topics do not exist: [non-existent-topic]",
  "groupId": "checkout-service-group",
  "consumerId": "checkout-service-abc123",
  "partitions": [
    // Only partitions from "orders" and "payments"
    // "non-existent-topic" is ignored
  ]
}
```

---

### **Scenario C: ALL Topics Don't Exist ❌**

**Request:**
```json
{
  "groupId": "checkout-service-group",
  "consumerId": "checkout-service-abc123",
  "topics": ["non-existent-1", "non-existent-2"]
}
```

**Response:**
```json
{
  "success": false,
  "errorMessage": "All topics do not exist: [non-existent-1, non-existent-2]",
  "groupId": null,
  "consumerId": null,
  "partitions": null
}
```

**Consumer Behavior:**
```java
// Consumer throws RuntimeException
throw new RuntimeException("Failed to join consumer group: All topics do not exist: [non-existent-1, non-existent-2]");
```

---

### **Scenario D: Empty Topics List ❌**

**Request:**
```json
{
  "groupId": "checkout-service-group",
  "consumerId": "checkout-service-abc123",
  "topics": []
}
```

**Response (Caught at Consumer Level):**
```
// Consumer throws IllegalArgumentException BEFORE sending request
throw new IllegalArgumentException("Topics cannot be null or empty");
```

---

## 4. Consumer State After Subscribe

### **Success Case:**

After a successful subscribe, the DMQConsumer updates these internal maps:

```java
// Example: Subscribed to ["orders", "payments"]
// orders has 2 partitions, payments has 1 partition

// 1. partitionMetadata map (stores ALL partition metadata)
partitionMetadata = {
  TopicPartition{topic='orders', partition=0} -> PartitionMetadata{
    topicName='orders', 
    partitionId=0, 
    leader=BrokerNode{id=1, host='localhost', port=9092},
    currentOffset=0,
    highWaterMark=100,
    isr=[1,2,3]
  },
  TopicPartition{topic='orders', partition=1} -> PartitionMetadata{...},
  TopicPartition{topic='payments', partition=0} -> PartitionMetadata{...}
}

// 2. fetchPositions map (initialized to start offsets)
fetchPositions = {
  TopicPartition{topic='orders', partition=0} -> 0,
  TopicPartition{topic='orders', partition=1} -> 0,
  TopicPartition{topic='payments', partition=0} -> 0
}

// 3. Consumer state
subscribed = true
generationId = 1  // From CES response
consumerId = "checkout-service-abc123"
```

### **Failure Case:**

```java
// Exception thrown, state remains unchanged
subscribed = false
partitionMetadata = {}  // Empty
fetchPositions = {}     // Empty
```

---

## 5. Key Points

### **Single Request for Multiple Topics**
- ✅ Consumer sends **ONE request** containing **ALL topics**
- ✅ Not separate requests per topic
- ✅ More efficient - single round trip

### **All-or-Nothing vs Partial Success**
- **Current Implementation (Phase 1):** All-or-nothing
  - If ANY topic doesn't exist → Entire subscription FAILS
  - Consumer throws exception
  
- **Future Enhancement (Phase 2+):** Partial success option
  - Subscribe to valid topics
  - Return warning for invalid topics
  - Consumer can decide whether to continue or fail

### **Response Contains ALL Partitions**
- CES returns metadata for **ALL partitions** from **ALL valid topics**
- Consumer stores metadata for all assigned partitions
- Consumer can poll from any assigned partition

### **Consumer Group Creation**
- If group doesn't exist → CES creates it automatically
- If group exists → Consumer joins existing group
- In Phase 1: One consumer per group (simple)
- In Phase 2+: Multiple consumers per group (rebalancing)

---

## 6. Error Handling Matrix

| Scenario | Request Status | CES Response | Consumer Behavior |
|----------|---------------|--------------|-------------------|
| All topics exist | ✅ Valid | `success=true`, returns all partitions | Subscribes successfully |
| Some topics missing | ⚠️ Partial | `success=false`, error message | Throws RuntimeException |
| All topics missing | ❌ Invalid | `success=false`, error message | Throws RuntimeException |
| Empty topics list | ❌ Invalid | Not sent (caught locally) | Throws IllegalArgumentException |
| Topics = null | ❌ Invalid | Not sent (caught locally) | Throws IllegalArgumentException |
| CES unavailable | ❌ Network | Connection error | Throws RuntimeException |

---

## 7. Example Test Cases

### **Test Case 1: Multiple Valid Topics**
```java
consumer.subscribe(Arrays.asList("orders", "payments", "inventory"));
// ✅ SUCCESS - Returns all partitions from all 3 topics
```

### **Test Case 2: Single Valid Topic**
```java
consumer.subscribe(Arrays.asList("orders"));
// ✅ SUCCESS - Returns partitions from "orders"
```

### **Test Case 3: Mix of Valid and Invalid**
```java
consumer.subscribe(Arrays.asList("orders", "fake-topic"));
// ❌ FAILS - Throws exception "Some topics do not exist: [fake-topic]"
```

### **Test Case 4: All Invalid Topics**
```java
consumer.subscribe(Arrays.asList("fake1", "fake2"));
// ❌ FAILS - Throws exception "All topics do not exist: [fake1, fake2]"
```

### **Test Case 5: Empty List**
```java
consumer.subscribe(Arrays.asList());
// ❌ FAILS - Throws IllegalArgumentException "Topics cannot be null or empty"
```

---

## 8. Mock Server Response Examples

The `test/mock-ces-server.py` demonstrates these scenarios:

```python
# Mock available topics
MOCK_TOPICS = {
    "orders": 2,      # 2 partitions
    "payments": 1,    # 1 partition
    "inventory": 2    # 2 partitions
}

# Scenario A: All topics exist
topics = ["orders", "payments"]
# Returns: 3 partitions (2 from orders + 1 from payments)

# Scenario B: Some topics missing
topics = ["orders", "fake-topic"]
# Returns: success=false, error="Topic not found: fake-topic"

# Scenario C: All topics missing
topics = ["fake1", "fake2"]
# Returns: success=false, error="Topics not found: fake1, fake2"
```

---

## Summary

**Request:** Single HTTP POST with **all topics** in one array  
**Response:** Either **all partitions** (success) OR **error message** (failure)  
**Behavior:** Currently **all-or-nothing** - any invalid topic causes complete failure  
**Future:** May support **partial success** - subscribe to valid topics, warn about invalid ones
