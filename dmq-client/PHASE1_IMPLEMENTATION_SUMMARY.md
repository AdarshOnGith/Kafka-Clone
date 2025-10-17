# Consumer Client Library - Phase 1 Implementation Summary

## ✅ Implementation Complete - October 17, 2025

### Architecture: Client-Side Consumer Library with Server-Side Metadata Service

---

## 🎯 Phase 1 Scope

**Goal:** Single consumer per group, simple message consumption

**What's Implemented:**
- ✅ Join consumer group via Consumer Egress Service (CES)
- ✅ Get partition metadata (leader, currentOffset, highWaterMark, ISR)
- ✅ Poll messages from storage nodes
- ✅ Track fetch positions locally
- ✅ Seek operations

**What's Deferred to Phase 2:**
- ⏳ Multi-member consumer groups
- ⏳ Client-side rebalancing
- ⏳ Offset commit
- ⏳ Heartbeat mechanism
- ⏳ Leave group

---

## 📡 API Contract with Consumer Egress Service

### **Endpoint:** `POST /api/consumer/join-group`

**Request:**
```json
{
  "groupId": "my-consumer-group",
  "consumerId": "consumer-abc123",
  "topics": ["orders", "payments"]
}
```

**Response from CES:**
```json
{
  "success": true,
  "groupId": "my-consumer-group",
  "partitions": [
    {
      "topic": "orders",
      "partition": 0,
      "leader": {
        "brokerId": 1,
        "host": "storage-node-1",
        "port": 9092
      },
      "currentOffset": 100,
      "highWaterMark": 250,
      "isr": [1, 2, 3]
    },
    {
      "topic": "orders",
      "partition": 1,
      "leader": {
        "brokerId": 2,
        "host": "storage-node-2",
        "port": 9092
      },
      "currentOffset": 50,
      "highWaterMark": 150,
      "isr": [2, 3]
    },
    {
      "topic": "payments",
      "partition": 0,
      "leader": {...},
      "currentOffset": 0,
      "highWaterMark": 80,
      "isr": [1, 3]
    }
  ]
}
```

### **CES Responsibilities:**

1. **Group Metadata (No Member Tracking):**
   - CES does **NOT** track which consumers are in the group
   - CES only knows: "group X is consuming topic Y"
   - Member assignment happens entirely in client library

2. **Partition Metadata Query:**
   - Query Metadata Service for topic partition info
   - Get leader broker for each partition
   - Get current offset for this group:
     - If offset committed → return committed offset
     - If no committed offset → return earliest available offset (based on policy)
   - Get high watermark (latest offset)
   - Get ISR list

3. **Response:**
   - Return complete partition metadata for requested topics
   - Client library decides which partitions to consume

**Note:** Client library handles all member assignment logic locally!

---

## 🔄 Complete Flow

```
┌─────────────────────────────────────────────────────────────┐
│  CLIENT APPLICATION                                         │
│                                                             │
│  ConsumerConfig config = ConsumerConfig.builder()          │
│      .metadataServiceUrl("http://localhost:8081")          │
│      .groupId("my-group")                                  │
│      .build();                                             │
│                                                             │
│  Consumer consumer = new DMQConsumer(config);              │
│  consumer.subscribe(Arrays.asList("orders"));              │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  DMQConsumer (Client Library)                               │
│                                                             │
│  1. Generate consumerId = "consumer-abc123"                │
│  2. Build request: {groupId, consumerId, topics}           │
│  3. Call ConsumerEgressClient.joinGroup()                  │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           │ HTTP POST /api/consumer/join-group
                           ▼
╔═════════════════════════════════════════════════════════════╗
║  CONSUMER EGRESS SERVICE (Server-Side)                      ║
║                                                             ║
║  1. Receive join request                                   ║
║  2. Check if group "my-group" has committed offsets        ║
║     (CES does NOT track members!)                          ║
║  3. Query Metadata Service:                                ║
║     - How many partitions for "orders"?                    ║
║     - Who is leader for each partition?                    ║
║     - What's committed offset for "my-group"?              ║
║     - What's high watermark for each partition?            ║
║     - What's ISR for each partition?                       ║
║  4. Build and return response (partition metadata only)    ║
╚══════════════════════════┬══════════════════════════════════╝
                           │
                           │ HTTP Response
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  DMQConsumer (Client Library)                               │
│                                                             │
│  6. Receive partition metadata                             │
│  7. CLIENT-SIDE: Decide which partitions to consume        │
│     Phase 1: Consume ALL partitions (single member)        │
│     Phase 2: Use rebalancing algorithm                     │
│  8. Store metadata for assigned partitions:                │
│     - Leader broker address                                │
│     - Current offset (from CES)                            │
│     - High watermark                                       │
│     - ISR                                                  │
│  9. Initialize fetch positions = currentOffset             │
│  10. Ready to poll!                                        │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ consumer.poll(1000)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  DMQConsumer.poll()                                         │
│                                                             │
│  For EACH partition:                                       │
│    - Get leader address from metadata                      │
│    - Get current fetch offset                              │
│    - HTTP POST to Storage Service:                         │
│      http://storage-node-1:9092/api/storage/fetch          │
│    - Receive messages                                      │
│    - Update fetch position                                 │
│                                                             │
│  Return all messages to client                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 📦 Files Updated

### **DTOs** (`dmq-common/dto/`)

1. **`ConsumerSubscriptionRequest.java`**
   ```java
   {
     groupId: "my-group",
     consumerId: "consumer-abc123",
     topics: ["orders"],
     // Future fields kept for Phase 2
     clientId, sessionTimeoutMs, heartbeatIntervalMs, autoOffsetReset
   }
   ```

2. **`ConsumerSubscriptionResponse.java`**
   ```java
   {
     success: true,
     groupId: "my-group",
     partitions: [PartitionMetadata...],
     // Future fields kept for Phase 2
     consumerId, generationId, coordinatorHost, coordinatorPort
   }
   ```

### **Models** (`dmq-common/model/`)

3. **`PartitionMetadata.java`** (Updated)
   ```java
   {
     topic: "orders",
     partition: 0,
     leader: BrokerNode,
     currentOffset: 100,      // CES provides this
     highWaterMark: 250,      // NEW: for lag monitoring
     isr: [1, 2, 3],          // NEW: ISR list
     // Future fields kept
     replicas, startOffset, endOffset
   }
   ```

### **Client** (`dmq-client/consumer/`)

4. **`ConsumerEgressClient.java`** (Updated)
   - ✅ `joinGroup()` - Join consumer group (Phase 1)
   - ✅ `fetchMessages()` - Fetch from storage nodes
   - ⏳ `commitOffsets()` - Kept for Phase 2
   - ⏳ `sendHeartbeat()` - Kept for Phase 2
   - ⏳ `leaveGroup()` - Kept for Phase 2

5. **`DMQConsumer.java`** (Updated)
   - ✅ `subscribe()` - Join group and get partition metadata
   - ✅ `poll()` - Fetch messages from all partitions
   - ✅ `seek()` - Seek to specific offset
   - ✅ `seekToBeginning()` / `seekToEnd()` - Seek operations
   - ⏳ `commitSync()` / `commitAsync()` - Stubbed for Phase 2
   - ⏳ `close()` - Simple cleanup (no leave group in Phase 1)

---

## 🎮 Usage Example

```java
// 1. Configure consumer
ConsumerConfig config = ConsumerConfig.builder()
    .metadataServiceUrl("http://localhost:8081")  // CES URL
    .groupId("order-processor-group")
    .clientId("order-processor")
    .build();

// 2. Create consumer
Consumer consumer = new DMQConsumer(config);

// 3. Subscribe (joins group, gets partition metadata)
consumer.subscribe(Arrays.asList("orders", "payments"));

// 4. Poll messages
while (true) {
    List<Message> messages = consumer.poll(1000);
    
    for (Message msg : messages) {
        // Process message
        System.out.println("Topic: " + msg.getTopic() + 
                          ", Partition: " + msg.getPartition() + 
                          ", Offset: " + msg.getOffset() +
                          ", Value: " + new String(msg.getValue()));
    }
    
    // Phase 2: Commit offsets
    // consumer.commitSync();
}

// 5. Close
consumer.close();
```

---

## 🔑 Key Design Decisions

### **1. CES Provides Partition Metadata Only**
- ✅ CES is stateless - no member tracking
- ✅ CES only provides: partition leaders, offsets, ISR
- ✅ Client library decides partition assignment

### **2. Client-Side Member Assignment**
- ✅ Phase 1: Single consumer = consume all partitions
- ✅ Phase 2: Multiple consumers = client-side rebalancing algorithm
- ✅ No server coordination needed

### **3. highWaterMark Added**
- ✅ For consumer lag monitoring
- ✅ For seekToEnd() operation
- ✅ Shows how far behind consumer is

### **4. Future-Proof DTOs**
- ✅ All Phase 2 fields kept but unused
- ✅ Easy to extend without breaking changes
- ✅ Comments indicate Phase 1 vs Phase 2

---

## ⚡ Phase 1 Limitations

| Feature | Status | Notes |
|---------|--------|-------|
| Single consumer per group | ✅ Works | Only one member allowed |
| Multi-member groups | ❌ Phase 2 | Rebalancing needed |
| Offset commit | ❌ Phase 2 | CES endpoint not called |
| Heartbeat | ❌ Phase 2 | No health monitoring |
| Leave group | ❌ Phase 2 | Consumer just closes |
| Auto-commit | ❌ Phase 2 | Manual only |

---

## 🚀 CES Implementation Guide (For Reference Only - Not Your Task)

### **Consumer Egress Service - What It Does:**

CES is a **stateless metadata gateway**. It does NOT track members!

```java
@RestController
@RequestMapping("/api/consumer")
public class ConsumerController {
    
    @PostMapping("/join-group")
    public ResponseEntity<ConsumerSubscriptionResponse> joinGroup(
        @RequestBody ConsumerSubscriptionRequest request) {
        
        String groupId = request.getGroupId();
        List<String> topics = request.getTopics();
        
        // CES does NOT track members - just returns metadata!
        
        // 1. Query Metadata Service for each topic:
        //    - Partition count
        //    - Leader broker for each partition
        //    - Committed offset for this group (or earliest if none)
        //    - High watermark
        //    - ISR list
        
        // 2. Build response with partition metadata
        List<PartitionMetadata> partitions = new ArrayList<>();
        
        for (String topic : topics) {
            // Query metadata service...
            for (int partitionId : partitions) {
                partitions.add(PartitionMetadata.builder()
                    .topic(topic)
                    .partition(partitionId)
                    .leader(leaderBroker)
                    .currentOffset(committedOffset != null ? committedOffset : earliestOffset)
                    .highWaterMark(latestOffset)
                    .isr(isrList)
                    .build());
            }
        }
        
        // 3. Return metadata - client decides what to do with it!
        return ResponseEntity.ok(ConsumerSubscriptionResponse.builder()
            .success(true)
            .groupId(groupId)
            .partitions(partitions)
            .build());
    }
}
```

**Note:** This is NOT your responsibility - someone else implements CES!

---

## 📊 Testing Checklist

- [ ] Consumer subscribes to single topic
- [ ] Consumer subscribes to multiple topics
- [ ] Consumer polls messages successfully
- [ ] Fetch position advances after poll
- [ ] Seek operations work correctly
- [ ] Consumer handles empty poll gracefully
- [ ] Consumer handles network errors
- [ ] Multiple consumers in different groups work independently
- [ ] CES creates group if not exists
- [ ] CES returns committed offset if available
- [ ] CES returns earliest offset if no commit

---

## 📝 Phase 2 Planning

### **Multi-Member Consumer Groups (Client-Side Logic):**

1. **Local Member Registry:**
   - Client library tracks local consumers (in-memory)
   - Each consumer instance knows about others in same JVM
   - No server-side coordination

2. **Client-Side Rebalancing Algorithm:**
   - When new consumer starts: redistribut partitions locally
   - Use round-robin, range, or consistent hashing
   - Each consumer independently decides its partitions

3. **Example:**
   ```
   Consumer-1 and Consumer-2 in same JVM, same group
   Topic "orders" has 3 partitions
   
   Client library logic:
   - Consumer-1 gets partitions [0, 1]
   - Consumer-2 gets partition [2]
   
   No server involved in this decision!
   ```

3. **Offset Commit:**
   - Implement commit endpoint
   - Store offsets in metadata service
   - Handle commit failures

4. **Heartbeat:**
   - Background thread sends heartbeats
   - CES marks members as dead if no heartbeat
   - Triggers rebalance

---

**Phase 1 Complete! ✅**  
**Ready for integration testing with CES!** 🚀
