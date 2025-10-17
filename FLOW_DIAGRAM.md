# Consumer Client Library - Flow Diagram

## Complete Subscription and Consumption Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENT APPLICATION                           │
│                                                                     │
│  ConsumerConfig config = ConsumerConfig.builder()                  │
│      .metadataServiceUrl("http://localhost:8081")                  │
│      .groupId("my-group").build();                                 │
│                                                                     │
│  Consumer consumer = new DMQConsumer(config);                      │
│  consumer.subscribe(Arrays.asList("orders"));                      │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               │ subscribe(topics)
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   DMQConsumer (Client Library)                      │
│                                                                     │
│  1. Generate unique consumerId = "consumer-abc123"                 │
│  2. Build ConsumerSubscriptionRequest:                             │
│     {                                                               │
│       "consumerGroup": "my-group",                                 │
│       "consumerId": "consumer-abc123",                             │
│       "topics": ["orders"],                                        │
│       "autoOffsetReset": "latest"                                  │
│     }                                                               │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               │ HTTP POST
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    ConsumerEgressClient                             │
│                                                                     │
│  POST http://localhost:8081/api/consumer/subscribe                 │
│  Content-Type: application/json                                    │
│                                                                     │
│  Body: ConsumerSubscriptionRequest                                 │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               │ Network Call
                               ▼
╔═════════════════════════════════════════════════════════════════════╗
║          CONSUMER EGRESS SERVICE (Kafka-Side)                       ║
║                    [TO BE IMPLEMENTED]                              ║
║                                                                     ║
║  1. Receive subscription request                                   ║
║  2. Check if consumer group "my-group" exists                      ║
║     └─ If NOT: Create new consumer group                           ║
║  3. Add consumer "consumer-abc123" as member                       ║
║  4. Since single member: Assign ALL partitions                     ║
║  5. Query Metadata Service for topic "orders":                     ║
║     └─ How many partitions? (e.g., 3)                              ║
║     └─ Who is leader for each partition?                           ║
║     └─ What's the offset for "my-group" in each partition?         ║
║  6. Build ConsumerSubscriptionResponse                             ║
╚══════════════════════════════┬══════════════════════════════════════╝
                               │
                               │ HTTP Response
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                ConsumerSubscriptionResponse                         │
│                                                                     │
│  {                                                                  │
│    "success": true,                                                 │
│    "consumerGroup": "my-group",                                    │
│    "consumerId": "consumer-abc123",                                │
│    "generationId": 1,                                              │
│    "assignments": [                                                 │
│      {                                                              │
│        "topic": "orders",                                          │
│        "partition": 0,                                             │
│        "leader": {                                                  │
│          "brokerId": 1,                                            │
│          "host": "storage-node-1",                                 │
│          "port": 9092                                              │
│        },                                                           │
│        "currentOffset": 100,  // Last committed offset             │
│        "startOffset": 0,      // Earliest available                │
│        "endOffset": 150       // Latest (high watermark)           │
│      },                                                             │
│      {                                                              │
│        "topic": "orders",                                          │
│        "partition": 1,                                             │
│        "leader": {...},                                            │
│        "currentOffset": 50,                                        │
│        ...                                                          │
│      },                                                             │
│      {                                                              │
│        "topic": "orders",                                          │
│        "partition": 2,                                             │
│        "leader": {...},                                            │
│        "currentOffset": -1,  // No committed offset                │
│        ...                                                          │
│      }                                                              │
│    ]                                                                │
│  }                                                                  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               │ Process Response
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   DMQConsumer (Client Library)                      │
│                                                                     │
│  3. Store partition assignments:                                   │
│     assignedPartitions = {                                         │
│       TopicPartition("orders", 0) → PartitionAssignment(...),     │
│       TopicPartition("orders", 1) → PartitionAssignment(...),     │
│       TopicPartition("orders", 2) → PartitionAssignment(...)      │
│     }                                                               │
│                                                                     │
│  4. Initialize fetch positions:                                    │
│     For partition 0:                                               │
│       currentOffset = 100 → fetchPositions["orders-0"] = 100      │
│     For partition 1:                                               │
│       currentOffset = 50 → fetchPositions["orders-1"] = 50        │
│     For partition 2:                                               │
│       currentOffset = -1 (not committed)                           │
│       autoOffsetReset = "latest"                                   │
│       → fetchPositions["orders-2"] = 0 (endOffset)                │
│                                                                     │
│  5. Mark as subscribed = true                                      │
│                                                                     │
│  ✅ READY TO CONSUME!                                               │
└─────────────────────────────────────────────────────────────────────┘

                                    │
                                    │ Now client calls poll()
                                    ▼

┌─────────────────────────────────────────────────────────────────────┐
│  CLIENT: List<Message> messages = consumer.poll(1000);             │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   DMQConsumer.poll(1000)                            │
│                                                                     │
│  For EACH assigned partition:                                      │
│    ┌─────────────────────────────────────────────┐                │
│    │ Partition: orders-0                         │                │
│    │ Fetch Offset: 100                           │                │
│    │ Leader: storage-node-1:9092                 │                │
│    │                                              │                │
│    │ → Fetch messages from leader                │                │
│    └──────────────┬──────────────────────────────┘                │
│                   │                                                 │
└───────────────────┼─────────────────────────────────────────────────┘
                    │
                    │ HTTP POST
                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│               POST http://storage-node-1:9092/api/storage/fetch     │
│                                                                     │
│  {                                                                  │
│    "consumerGroup": "my-group",                                    │
│    "topic": "orders",                                              │
│    "partition": 0,                                                 │
│    "offset": 100,           // Start reading from here             │
│    "maxMessages": 500,                                             │
│    "maxWaitMs": 1000                                               │
│  }                                                                  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
╔═════════════════════════════════════════════════════════════════════╗
║            STORAGE SERVICE (Leader for partition 0)                 ║
║                                                                     ║
║  1. Read WAL from offset 100                                       ║
║  2. Return messages [100, 101, 102, ..., 120]                     ║
╚══════════════════════════════┬══════════════════════════════════════╝
                               │
                               │ HTTP Response
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      ConsumeResponse                                │
│  {                                                                  │
│    "success": true,                                                 │
│    "messages": [                                                    │
│      {                                                              │
│        "topic": "orders",                                          │
│        "partition": 0,                                             │
│        "offset": 100,                                              │
│        "key": "order-1",                                           │
│        "value": "..."                                              │
│      },                                                             │
│      ... (20 more messages)                                        │
│    ],                                                               │
│    "highWaterMark": 150                                            │
│  }                                                                  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   DMQConsumer.poll() continues                      │
│                                                                     │
│  Received 21 messages from partition 0                             │
│  Last offset: 120                                                  │
│  → Update: fetchPositions["orders-0"] = 121  (next to fetch)      │
│                                                                     │
│  Repeat for partition 1 and partition 2...                         │
│                                                                     │
│  Return all messages to client application                         │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  CLIENT: Process messages and commit                                │
│                                                                     │
│  for (Message msg : messages) {                                    │
│      // Process message                                             │
│  }                                                                  │
│  consumer.commitSync();                                            │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   DMQConsumer.commitSync()                          │
│                                                                     │
│  Build offset list:                                                 │
│  [                                                                  │
│    {topic: "orders", partition: 0, offset: 121},                  │
│    {topic: "orders", partition: 1, offset: 65},                   │
│    {topic: "orders", partition: 2, offset: 10}                    │
│  ]                                                                  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               │ HTTP POST
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│       POST http://localhost:8081/api/consumer/commit                │
│                                                                     │
│  {                                                                  │
│    "consumerGroup": "my-group",                                    │
│    "offsets": [                                                     │
│      {topic: "orders", partition: 0, offset: 121},                │
│      {topic: "orders", partition: 1, offset: 65},                 │
│      {topic: "orders", partition: 2, offset: 10}                  │
│    ]                                                                │
│  }                                                                  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
╔═════════════════════════════════════════════════════════════════════╗
║          CONSUMER EGRESS SERVICE                                    ║
║                                                                     ║
║  1. Receive commit request                                         ║
║  2. Store offsets in Metadata Service                              ║
║  3. Return success                                                  ║
╚══════════════════════════════┬══════════════════════════════════════╝
                               │
                               │ Success
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   DMQConsumer                                       │
│                                                                     │
│  Update committedOffsets:                                          │
│    committedOffsets["orders-0"] = 121                              │
│    committedOffsets["orders-1"] = 65                               │
│    committedOffsets["orders-2"] = 10                               │
│                                                                     │
│  ✅ Commit successful                                               │
└─────────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════

                        CLEANUP ON CLOSE

═══════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────┐
│  CLIENT: consumer.close();                                          │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   DMQConsumer.close()                               │
│                                                                     │
│  1. Stop heartbeat thread (if running)                             │
│  2. Send leave group request                                       │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               │ HTTP POST
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│       POST http://localhost:8081/api/consumer/leave                 │
│                                                                     │
│  {                                                                  │
│    "consumerGroup": "my-group",                                    │
│    "consumerId": "consumer-abc123"                                 │
│  }                                                                  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
╔═════════════════════════════════════════════════════════════════════╗
║          CONSUMER EGRESS SERVICE                                    ║
║                                                                     ║
║  1. Remove consumer from group                                     ║
║  2. Since last member: Mark group as empty                         ║
║  3. Return success                                                  ║
╚══════════════════════════════┬══════════════════════════════════════╝
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   DMQConsumer                                       │
│                                                                     │
│  3. Clear all state:                                               │
│     - assignedPartitions.clear()                                   │
│     - fetchPositions.clear()                                       │
│     - committedOffsets.clear()                                     │
│  4. Mark closed = true                                             │
│                                                                     │
│  ✅ Consumer closed cleanly                                         │
└─────────────────────────────────────────────────────────────────────┘
```

## Summary of Responsibilities

### Client Library (✅ IMPLEMENTED):
- Generate unique consumer IDs
- Build subscription requests
- Handle HTTP communication
- Store partition assignments
- Track fetch positions
- Manage offset commits
- Handle consumer lifecycle

### Consumer Egress Service (⏳ TO BE IMPLEMENTED):
- Create/manage consumer groups
- Assign partitions to consumers
- Query metadata service for topology
- Track consumer group offsets
- Handle member join/leave
- Coordinate with metadata service

### Storage Service (⏳ VERIFY EXISTS):
- Serve messages from WAL
- Respect leader/follower roles
- Return messages from specific offset
- Track high watermark

### Metadata Service (⏳ VERIFY EXISTS):
- Store topic/partition metadata
- Store consumer group offsets
- Provide cluster topology
- Track partition leaders
