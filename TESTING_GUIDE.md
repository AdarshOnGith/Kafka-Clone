# Kafka-Clone Testing Guide

## Overview
This document provides testing procedures for the Kafka-Clone distributed messaging system.

## 1. Metadata Setup (Required for Replication Testing)

### 1.1 Metadata Update JSON Format

Before testing publish/replication flows, you need to populate the MetadataStore with cluster information.

#### Sample Metadata Update Request

```json
{
  "brokers": [
    {
      "id": 1,
      "host": "localhost",
      "port": 8081,
      "isAlive": true,
      "lastHeartbeat": 1703123456789
    },
    {
      "id": 2,
      "host": "localhost",
      "port": 8082,
      "isAlive": true,
      "lastHeartbeat": 1703123456789
    },
    {
      "id": 3,
      "host": "localhost",
      "port": 8083,
      "isAlive": true,
      "lastHeartbeat": 1703123456789
    }
  ],
  "partitions": [
    {
      "topic": "test-topic",
      "partition": 0,
      "leaderId": 1,
      "followerIds": [2, 3],
      "isrIds": [1, 2, 3],
      "leaderEpoch": 1
    },
    {
      "topic": "orders",
      "partition": 0,
      "leaderId": 2,
      "followerIds": [1, 3],
      "isrIds": [2, 1],
      "leaderEpoch": 1
    }
  ],
  "timestamp": 1703123456789
}
```

### 1.2 How to Populate Metadata

#### Option 1: Using curl (Manual Testing)
```bash
curl -X POST http://localhost:8081/api/v1/storage/metadata \
  -H "Content-Type: application/json" \
  -d @metadata-update.json
```

#### Option 2: Using HTTP Client (Postman/Insomnia)
- Method: POST
- URL: `http://localhost:{port}/api/v1/storage/metadata`
- Body: Raw JSON (paste the sample above)
- Content-Type: `application/json`

#### Option 3: Programmatic (Future)
```java
// Will be implemented when metadata service is ready
metadataService.pushMetadataToStorageNodes(metadataUpdate);
```

## 2. Single Message Publish Flow

### 2.1 Test Setup
1. Start 3 storage service instances:
   - Broker 1: `localhost:8081` (leader for test-topic-0)
   - Broker 2: `localhost:8082` (follower for test-topic-0)
   - Broker 3: `localhost:8083` (follower for test-topic-0)

2. Populate metadata using the JSON above

### 2.2 Single Message Publish Request
```json
{
  "topic": "test-topic",
  "partition": 0,
  "messages": [
    {
      "key": "user123",
      "value": "SGVsbG8gV29ybGQ=",  // Base64 encoded "Hello World"
      "timestamp": 1703123456789
    }
  ],
  "producerId": "producer-1",
  "producerEpoch": 1,
  "requiredAcks": -1,
  "timeoutMs": 5000
}
```

### 2.3 Expected Flow
1. **Producer** → **Broker 1** (leader): Send publish request
2. **Broker 1** validates leadership, appends to WAL, replicates to ISR (brokers 2 & 3)
3. **Broker 2 & 3** receive replication requests, append to their WALs, respond with ACK
4. **Broker 1** waits for all ISR ACKs, updates high watermark, responds to producer

### 2.4 Verification Steps
1. Check Broker 1 logs for successful replication
2. Verify message appears in all 3 brokers' WALs
3. Check high watermark updates
4. Verify producer receives success response with offset

## 3. Batch Message Publish Flow

### 3.1 Batch Message Request
```json
{
  "topic": "test-topic",
  "partition": 0,
  "messages": [
    {
      "key": "batch-1",
      "value": "TWVzc2FnZSAx",  // Base64 encoded "Message 1"
      "timestamp": 1703123456789
    },
    {
      "key": "batch-2",
      "value": "TWVzc2FnZSAy",  // Base64 encoded "Message 2"
      "timestamp": 1703123456790
    },
    {
      "key": "batch-3",
      "value": "TWVzc2FnZSAz",  // Base64 encoded "Message 3"
      "timestamp": 1703123456791
    }
  ],
  "producerId": "producer-batch",
  "producerEpoch": 1,
  "requiredAcks": 1,
  "timeoutMs": 5000
}
```

### 3.2 Expected Flow
Same as single message but:
- All messages batched together
- Single replication request with all messages
- Offsets assigned sequentially
- `requiredAcks: 1` means only leader ACK needed (faster)

## 4. Replication Scenarios to Test

### 4.1 Different Ack Levels
- `acks: 0` - No replication, fire-and-forget
- `acks: 1` - Leader ACK only
- `acks: -1` - All ISR must ACK

### 4.2 Failure Scenarios
- ISR broker down during replication
- Network partition between brokers
- Leader election during replication

### 4.3 Validation Checks
- Non-leader broker rejects publish requests
- Invalid topic/partition validation
- Message size limits (TODO)
- Producer idempotency (TODO)

## 5. Consumer Flow Testing (Future)

Once WAL.read() is implemented, test:
- Single message consumption
- Batch consumption
- Offset management
- High watermark tracking

## 6. Monitoring & Debugging

### 6.1 Key Log Messages to Watch
```
- "Starting replication for topic-partition"
- "Replication completed for topic-partition"
- "Appended message at offset"
- "Updated High Watermark"
```

### 6.2 Health Check Endpoints
- `GET /api/v1/storage/health` - Service health
- `GET /api/v1/storage/partitions/{topic}/{partition}/high-water-mark` - HWM

## 7. TODO Items for Complete Testing

- [ ] Implement WAL.read() for consumer testing
- [ ] Add message size validation
- [ ] Implement producer idempotency
- [ ] Add leader epoch validation
- [ ] Create automated test scripts
- [ ] Add performance benchmarking
- [ ] Test with real network partitions