# Consumer Implementation - Checklist & Next Steps

## ✅ COMPLETED - Client Library Implementation

### Phase 1: Basic Consumer (Single Member Groups) - DONE ✅

- [x] **DTOs Created**
  - [x] `ConsumerSubscriptionRequest.java`
  - [x] `ConsumerSubscriptionResponse.java`
  
- [x] **Models Created**
  - [x] `PartitionAssignment.java`
  
- [x] **HTTP Client**
  - [x] `ConsumerEgressClient.java` with all endpoints
  
- [x] **Consumer Implementation**
  - [x] `DMQConsumer.java` with full functionality
  - [x] Unique consumer ID generation
  - [x] Subscription logic
  - [x] Partition assignment handling
  - [x] Offset initialization (earliest/latest/none)
  - [x] Poll from all partitions
  - [x] Fetch position tracking
  - [x] Synchronous offset commit
  - [x] Seek operations (seek, seekToBeginning, seekToEnd)
  - [x] Proper close with group leave

---

## ⏳ TODO - Server Side Implementation

### Phase 1: Consumer Egress Service

You need to implement the **Consumer Egress Service** with the following endpoints:

#### 1. Subscribe Endpoint ⏳
```
POST /api/consumer/subscribe
```

**Implementation Tasks:**
- [ ] Create `ConsumerController.java` with `/subscribe` endpoint
- [ ] Create `ConsumerGroupService.java` for group management
- [ ] **Consumer Group Logic:**
  - [ ] Check if consumer group exists in metadata service
  - [ ] If NOT exists: Create new consumer group
  - [ ] Add consumer as member of group
  - [ ] For single-member: Assign ALL partitions to this consumer
- [ ] **Metadata Query Logic:**
  - [ ] Query metadata service for topic information
  - [ ] Get partition count for each topic
  - [ ] Get leader broker for each partition
  - [ ] Get current offset for this consumer group (from metadata service)
  - [ ] Get start/end offsets for each partition
- [ ] **Build Response:**
  - [ ] Build `ConsumerSubscriptionResponse` with all assignments
  - [ ] Include partition metadata (leader, offsets, etc.)
  - [ ] Return to client

**Example Implementation Structure:**
```java
@RestController
@RequestMapping("/api/consumer")
public class ConsumerController {
    
    @PostMapping("/subscribe")
    public ResponseEntity<ConsumerSubscriptionResponse> subscribe(
        @RequestBody ConsumerSubscriptionRequest request) {
        
        // 1. Validate request
        // 2. Call ConsumerGroupService to join/create group
        // 3. Get partition assignments
        // 4. Query metadata for each partition
        // 5. Build and return response
    }
}
```

#### 2. Commit Offset Endpoint ⏳
```
POST /api/consumer/commit
```

**Implementation Tasks:**
- [ ] Create commit endpoint in `ConsumerController.java`
- [ ] **Offset Storage Logic:**
  - [ ] Receive list of offsets from client
  - [ ] Validate consumer group exists
  - [ ] Store offsets in metadata service (database)
  - [ ] Return success/failure

**Database Schema Needed:**
```sql
CREATE TABLE consumer_offsets (
    consumer_group VARCHAR(255),
    topic VARCHAR(255),
    partition INT,
    offset BIGINT,
    timestamp TIMESTAMP,
    PRIMARY KEY (consumer_group, topic, partition)
);
```

#### 3. Leave Group Endpoint ⏳
```
POST /api/consumer/leave
```

**Implementation Tasks:**
- [ ] Create leave endpoint in `ConsumerController.java`
- [ ] **Leave Group Logic:**
  - [ ] Remove consumer from consumer group
  - [ ] If last member: Mark group as empty (don't delete yet)
  - [ ] Return success

**Database Schema Needed:**
```sql
CREATE TABLE consumer_group_members (
    consumer_group VARCHAR(255),
    consumer_id VARCHAR(255),
    joined_at TIMESTAMP,
    last_heartbeat TIMESTAMP,
    PRIMARY KEY (consumer_group, consumer_id)
);
```

---

### Phase 1: Metadata Service Extensions

#### Update Metadata Service ⏳

**Implementation Tasks:**
- [ ] **Topic Metadata Endpoints:**
  - [ ] `GET /api/metadata/topics/{topicName}` - Get topic details
  - [ ] Returns: partition count, leader for each partition, ISR, etc.
  
- [ ] **Partition Leader Endpoints:**
  - [ ] `GET /api/metadata/topics/{topicName}/partitions/{partitionId}/leader`
  - [ ] Returns: broker node info (host, port, brokerId)
  
- [ ] **Offset Management:**
  - [ ] `GET /api/metadata/consumer-groups/{groupId}/offsets` - Get all offsets
  - [ ] `GET /api/metadata/consumer-groups/{groupId}/offsets/{topic}/{partition}` - Get specific offset
  - [ ] `POST /api/metadata/consumer-groups/{groupId}/offsets` - Store offsets
  
- [ ] **Consumer Group Management:**
  - [ ] `POST /api/metadata/consumer-groups` - Create consumer group
  - [ ] `GET /api/metadata/consumer-groups/{groupId}` - Get group info
  - [ ] `POST /api/metadata/consumer-groups/{groupId}/members` - Add member
  - [ ] `DELETE /api/metadata/consumer-groups/{groupId}/members/{consumerId}` - Remove member

---

### Phase 1: Storage Service Verification

#### Verify Fetch Endpoint Exists ⏳

**Check if Storage Service has:**
```
POST /api/storage/fetch
```

**Required Functionality:**
- [ ] Accept `ConsumeRequest` with topic, partition, offset
- [ ] Read from WAL starting at specified offset
- [ ] Return list of messages
- [ ] Include high watermark in response
- [ ] Handle errors gracefully

---

## 🧪 Testing Plan

### Unit Tests ⏳
- [ ] Test `DMQConsumer.subscribe()` with mock HTTP client
- [ ] Test `DMQConsumer.poll()` with different scenarios
- [ ] Test offset initialization (earliest/latest/none)
- [ ] Test `commitSync()` success/failure
- [ ] Test `close()` cleanup

### Integration Tests ⏳
- [ ] **Test 1: Subscribe Flow**
  - [ ] Start Consumer Egress Service
  - [ ] Create consumer with config
  - [ ] Subscribe to topic
  - [ ] Verify partition assignments received
  - [ ] Verify fetch positions initialized
  
- [ ] **Test 2: Poll Flow**
  - [ ] Subscribe to topic with test data
  - [ ] Poll messages
  - [ ] Verify messages received
  - [ ] Verify fetch positions updated
  
- [ ] **Test 3: Commit Flow**
  - [ ] Poll some messages
  - [ ] Commit offsets
  - [ ] Verify offsets stored in metadata service
  - [ ] Create new consumer in same group
  - [ ] Verify it resumes from committed offset
  
- [ ] **Test 4: Offset Reset Policy**
  - [ ] Test with `autoOffsetReset=earliest`
  - [ ] Test with `autoOffsetReset=latest`
  - [ ] Test with `autoOffsetReset=none` (should throw error)
  
- [ ] **Test 5: Close Flow**
  - [ ] Subscribe to topic
  - [ ] Close consumer
  - [ ] Verify leave group called
  - [ ] Verify state cleaned up

### End-to-End Test ⏳
```java
@Test
public void testCompleteConsumerFlow() {
    // 1. Create consumer
    ConsumerConfig config = ConsumerConfig.builder()
        .metadataServiceUrl("http://localhost:8081")
        .groupId("test-group")
        .autoOffsetReset("earliest")
        .build();
    Consumer consumer = new DMQConsumer(config);
    
    // 2. Subscribe
    consumer.subscribe(Arrays.asList("test-topic"));
    
    // 3. Poll messages
    List<Message> messages = consumer.poll(1000);
    assertFalse(messages.isEmpty());
    
    // 4. Commit
    consumer.commitSync();
    
    // 5. Close
    consumer.close();
}
```

---

## 📊 Monitoring & Metrics (Future)

### Phase 2: Metrics to Add ⏳
- [ ] Consumer lag per partition
- [ ] Messages consumed per second
- [ ] Commit latency
- [ ] Poll latency
- [ ] Rebalance count
- [ ] Consumer group member count

---

## 🚀 Phase 2: Advanced Features (Future)

### Multi-Member Consumer Groups ⏳
- [ ] Implement rebalancing protocol
- [ ] Add heartbeat thread
- [ ] Implement partition redistribution
- [ ] Handle consumer failures
- [ ] Implement generation ID tracking

### Performance Optimizations ⏳
- [ ] Implement async commit with callbacks
- [ ] Add connection pooling
- [ ] Implement batch fetching optimization
- [ ] Add prefetching logic
- [ ] Implement compression support

### Advanced Features ⏳
- [ ] Consumer interceptors
- [ ] Pause/resume partitions
- [ ] Pattern-based topic subscription
- [ ] Seek to timestamp
- [ ] Transaction support
- [ ] Exactly-once semantics

---

## 📝 Documentation TODO

- [ ] API documentation for Consumer Egress Service
- [ ] Sequence diagrams for each flow
- [ ] Error handling guide
- [ ] Configuration tuning guide
- [ ] Troubleshooting guide

---

## 🎯 Immediate Next Step

**START HERE:**

1. **Implement Consumer Egress Service - Subscribe Endpoint**
   - Create `ConsumerEgressService` Spring Boot application
   - Add `ConsumerController` with `/api/consumer/subscribe`
   - Implement basic consumer group creation (single member)
   - Query metadata service for partition info
   - Return partition assignments to client

2. **Test the Subscribe Flow**
   - Run the client library
   - Call `subscribe()`
   - Verify response received
   - Verify assignments stored

3. **Implement Storage Service Fetch (if not exists)**
   - Add `/api/storage/fetch` endpoint
   - Read from WAL at specified offset
   - Return messages

4. **Test End-to-End**
   - Subscribe → Poll → Process → Commit → Close
   - Verify complete flow works!

---

## 💡 Tips

- Start with **in-memory storage** for consumer groups (HashMap) before adding database
- Use **H2 database** for quick prototyping
- Add **extensive logging** to debug issues
- Test with **single partition** first, then multiple
- Use **Postman** to test endpoints manually before integration

---

## 📞 Questions to Resolve

- [ ] Where should consumer group metadata be stored? (Metadata service DB?)
- [ ] Should consumer groups be persistent or ephemeral?
- [ ] What happens if consumer crashes without leaving group?
- [ ] How long to keep committed offsets for inactive groups?
- [ ] Should we support regex patterns for topic subscription?

---

**Good luck with the implementation! The client library is ready and waiting for the server side! 🚀**
