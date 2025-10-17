# Consumer Client Library Implementation

## Overview
Implemented the consumer-side library functionality for consuming messages from topics. The library communicates with the Consumer Egress Service to get metadata and fetch messages from Storage Services.

## Implementation Date
October 17, 2025

---

## Architecture Flow

```
Client Application
    │
    ├─> subscribe(topics)
    │
    v
DMQConsumer (Client Library)
    │
    ├─> Generates unique consumerId
    ├─> Creates ConsumerSubscriptionRequest
    │
    v
ConsumerEgressClient
    │
    ├─> HTTP POST to /api/consumer/subscribe
    │
    v
Consumer Egress Service (Kafka-side)
    │
    ├─> Creates/joins consumer group
    ├─> Assigns partitions (single member per group for now)
    ├─> Fetches partition metadata:
    │   - Partition count
    │   - Leader broker info
    │   - Current offsets
    │   - Start/End offsets (for reset policy)
    │
    v
ConsumerSubscriptionResponse
    │
    ├─> Returns to DMQConsumer
    │
    v
DMQConsumer
    │
    ├─> Stores partition assignments
    ├─> Initializes fetch positions based on:
    │   - Committed offset (if exists)
    │   - auto.offset.reset policy (earliest/latest/none)
    │
    └─> Ready to poll messages
```

---

## Components Created

### 1. **New DTOs** (`dmq-common/dto/`)

#### `ConsumerSubscriptionRequest.java`
- Consumer group ID
- Consumer ID (unique identifier)
- List of topics to subscribe
- Consumer settings (timeout, heartbeat, offset reset)

#### `ConsumerSubscriptionResponse.java`
- Success/error status
- Consumer group and consumer ID
- Generation ID (for rebalancing)
- List of partition assignments
- Coordinator info

### 2. **New Model** (`dmq-common/model/`)

#### `PartitionAssignment.java`
Represents a partition assignment with complete metadata:
- Topic and partition ID
- Leader broker info (host, port)
- Current offset for the consumer group
- Start offset (earliest available)
- End offset (high watermark/latest)
- Replica count

### 3. **ConsumerEgressClient** (`dmq-client/consumer/`)

HTTP client for communicating with Kafka-side services:

**Methods:**
- `subscribe()` - Subscribe to topics and get assignments
- `fetchMessages()` - Fetch messages from storage leader
- `commitOffsets()` - Commit offsets to metadata service
- `sendHeartbeat()` - Maintain group membership (for future)
- `leaveGroup()` - Leave consumer group on close

### 4. **Enhanced DMQConsumer** (`dmq-client/consumer/`)

Main consumer implementation with state management:

**Key Features:**
- Unique consumer ID generation
- Partition assignment tracking
- Fetch position management
- Committed offset tracking
- Auto-offset-reset policy handling

**Implemented Methods:**

#### `subscribe(Collection<String> topics)`
1. Validates input and consumer state
2. Builds subscription request with config
3. Sends request to Consumer Egress Service
4. Receives partition assignments with metadata
5. Initializes fetch positions based on:
   - Existing committed offsets (resume)
   - auto.offset.reset policy (new consumer)
     - `earliest`: Start from beginning
     - `latest`: Start from current end
     - `none`: Throw error if no committed offset

#### `poll(long timeoutMs)`
1. Checks if subscribed and has assignments
2. For each assigned partition:
   - Gets current fetch offset
   - Builds ConsumeRequest
   - Fetches from leader broker via storage service
   - Updates fetch position after receiving messages
3. Returns all messages from all partitions
4. Auto-commit support (placeholder for now)

#### `commitSync()`
1. Builds offset list from current fetch positions
2. Sends commit request to egress service
3. Updates committed offsets on success
4. Throws exception on failure

#### `commitAsync()`
- Placeholder: Falls back to sync commit for now
- TODO: Implement async commit in separate thread

#### `seek()`, `seekToBeginning()`, `seekToEnd()`
- Manual offset control
- Updates fetch positions based on partition metadata

#### `close()`
1. Stops heartbeat thread (if running)
2. Leaves consumer group
3. Clears all state
4. Marks consumer as closed

---

## Consumer Group Strategy

**Current Implementation (Phase 1):**
- Each consumer creates/joins a consumer group with group ID
- Single member per group (no multi-member rebalancing yet)
- Consumer gets ALL partitions assigned to it
- Simple offset management per partition

**Future Enhancement:**
- Multi-member groups
- Rebalancing protocol
- Partition redistribution
- Cooperative rebalancing

---

## Configuration Options Used

```java
ConsumerConfig.builder()
    .metadataServiceUrl("http://localhost:8081")  // Egress service URL
    .groupId("my-consumer-group")                 // Consumer group ID
    .clientId("my-consumer")                      // Client identifier
    .enableAutoCommit(true)                       // Auto-commit offsets
    .autoCommitIntervalMs(5000L)                  // Commit interval
    .autoOffsetReset("latest")                    // earliest|latest|none
    .maxPollRecords(500)                          // Max messages per poll
    .fetchMinBytes(1)                             // Min bytes to fetch
    .fetchMaxBytes(52428800)                      // Max bytes (50MB)
    .fetchMaxWaitMs(500L)                         // Max wait time
    .sessionTimeoutMs(10000L)                     // Session timeout
    .heartbeatIntervalMs(3000L)                   // Heartbeat interval
    .build();
```

---

## API Endpoints Expected (Egress Service)

### 1. **Subscribe** - `POST /api/consumer/subscribe`
**Request:**
```json
{
  "consumerGroup": "my-group",
  "consumerId": "consumer-abc123",
  "topics": ["topic1", "topic2"],
  "clientId": "my-app",
  "sessionTimeoutMs": 10000,
  "heartbeatIntervalMs": 3000,
  "autoOffsetReset": "latest"
}
```

**Response:**
```json
{
  "success": true,
  "consumerGroup": "my-group",
  "consumerId": "consumer-abc123",
  "generationId": 1,
  "assignments": [
    {
      "topic": "topic1",
      "partition": 0,
      "leader": {
        "brokerId": 1,
        "host": "localhost",
        "port": 9092
      },
      "currentOffset": 100,
      "startOffset": 0,
      "endOffset": 150,
      "replicaCount": 3
    }
  ],
  "coordinatorHost": "localhost",
  "coordinatorPort": 8081
}
```

### 2. **Commit Offsets** - `POST /api/consumer/commit`
**Request:**
```json
{
  "consumerGroup": "my-group",
  "offsets": [
    {
      "topic": "topic1",
      "partition": 0,
      "offset": 120,
      "timestamp": 1697510400000
    }
  ]
}
```

### 3. **Heartbeat** - `POST /api/consumer/heartbeat`
(For future multi-member groups)

### 4. **Leave Group** - `POST /api/consumer/leave`
**Request:**
```json
{
  "consumerGroup": "my-group",
  "consumerId": "consumer-abc123"
}
```

---

## Storage Service API Expected

### **Fetch Messages** - `POST /api/storage/fetch`
**Request:**
```json
{
  "consumerGroup": "my-group",
  "topic": "topic1",
  "partition": 0,
  "offset": 100,
  "maxMessages": 500,
  "maxWaitMs": 500,
  "minBytes": 1,
  "maxBytes": 52428800
}
```

**Response:**
```json
{
  "success": true,
  "messages": [
    {
      "key": "key1",
      "value": "...",
      "topic": "topic1",
      "partition": 0,
      "offset": 100,
      "timestamp": 1697510400000
    }
  ],
  "highWaterMark": 150
}
```

---

## Usage Example

```java
// Create consumer configuration
ConsumerConfig config = ConsumerConfig.builder()
    .metadataServiceUrl("http://localhost:8081")
    .groupId("my-consumer-group")
    .clientId("my-app")
    .enableAutoCommit(true)
    .autoOffsetReset("latest")
    .build();

// Create consumer
Consumer consumer = new DMQConsumer(config);

// Subscribe to topics
// This will:
// 1. Contact Consumer Egress Service
// 2. Create/join consumer group
// 3. Get partition assignments with metadata
// 4. Initialize fetch positions
consumer.subscribe(Arrays.asList("orders", "payments"));

// Poll for messages
while (true) {
    List<Message> messages = consumer.poll(1000);
    
    for (Message msg : messages) {
        // Process message
        System.out.println("Received: " + new String(msg.getValue()));
    }
    
    // Commit offsets (if auto-commit is disabled)
    if (!config.getEnableAutoCommit()) {
        consumer.commitSync();
    }
}

// Close consumer (leaves group, commits final offsets)
consumer.close();
```

---

## State Management

### Internal State Tracking:

1. **`assignedPartitions`** - Map of TopicPartition → PartitionAssignment
   - Contains full metadata for each assigned partition
   - Leader broker info for routing fetch requests

2. **`fetchPositions`** - Map of TopicPartition → Long
   - Current offset to fetch next (increments after each poll)
   - Updated after each successful message fetch

3. **`committedOffsets`** - Map of TopicPartition → Long
   - Last committed offset for each partition
   - Updated after successful commit

4. **Consumer Metadata:**
   - `consumerId`: Unique identifier (clientId + UUID)
   - `generationId`: For tracking rebalancing (future use)
   - `subscribed`: Whether consumer has active subscription

---

## TODO / Future Enhancements

- [ ] Implement heartbeat thread for maintaining group membership
- [ ] Implement async commit with callback
- [ ] Add proper auto-commit with interval checking
- [ ] Multi-member consumer group support
- [ ] Rebalancing protocol
- [ ] Partition pause/resume
- [ ] Consumer interceptors
- [ ] Metrics and monitoring
- [ ] Error handling and retry logic
- [ ] Connection pooling for HTTP clients
- [ ] Support for patterns in topic subscription

---

## Testing Checklist

To test this implementation, you need:

1. **Consumer Egress Service** running at configured URL
   - Must implement `/api/consumer/subscribe` endpoint
   - Must return partition assignments with metadata

2. **Storage Service(s)** running as partition leaders
   - Must implement `/api/storage/fetch` endpoint
   - Must serve messages from specified offset

3. **Metadata Service** to store committed offsets
   - Egress service should delegate to metadata service

---

## Key Design Decisions

1. **Single Consumer Group Member**: Simplifies initial implementation, defers complex rebalancing

2. **HTTP REST Communication**: Uses Spring's RestTemplate for simplicity, can be replaced with more efficient protocol later

3. **Offset Reset Policy**: Supports Kafka-like behavior (earliest/latest/none)

4. **Synchronous Polling**: Current implementation is synchronous, can be enhanced with async/reactive patterns

5. **State in Memory**: All state kept in memory, lost on restart (committed offsets persisted server-side)

---

## Integration Points

**Client Library (this implementation)**
- Manages consumer state and logic
- Handles subscription and consumption flow
- Communicates via HTTP

**Consumer Egress Service (to be implemented)**
- Receives subscription requests
- Manages consumer groups
- Assigns partitions
- Tracks consumer group offsets
- Acts as gateway to metadata/storage services

**Storage Service**
- Serves messages from WAL
- Respects leader/follower roles
- Returns messages from specified offset

**Metadata Service**
- Stores topic/partition metadata
- Stores consumer group offsets
- Provides cluster topology information
