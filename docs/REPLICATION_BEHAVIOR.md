# Replication Behavior in DMQ Storage Service

## Overview
This document explains how message replication works across different `acks` (acknowledgment) settings and how the High Water Mark (HWM) is managed.

## Key Concepts

### ISR (In-Sync Replicas)
- **Definition**: Set of replicas that are caught up with the leader
- **Purpose**: Ensures data durability and consistency
- **Management**: Controlled by metadata service based on replication lag
- **Replication Target**: **ONLY ISR members receive replication requests**

### High Water Mark (HWM)
- **Definition**: Highest offset that has been replicated to min.insync.replicas
- **Purpose**: Marks the point up to which consumers can safely read (committed messages)
- **Update Condition**: Advanced ONLY when `min.insync.replicas` successfully replicate

## Replication Behavior by Acks Type

### 🚀 `acks=0` (Fire and Forget)

**Producer Acknowledgment:**
- ✅ Returns **immediately** after broker receives the request
- ⚠️ Does NOT wait for leader write
- ⚠️ Does NOT wait for follower replication

**Leader Behavior:**
1. Writes message to local WAL
2. **Sends replication requests to ALL ISR followers asynchronously**
3. Continues without waiting for responses

**Follower Replication:**
- ✅ **All ISR followers receive replication requests**
- ⚠️ Non-ISR followers are excluded
- 🔄 Replication happens in background (fire-and-forget)

**HWM Update:**
- 🔄 Updated **asynchronously** after `min.insync.replicas` acknowledge
- ⏰ May lag behind LEO (Log End Offset)

**Use Case:** 
- Maximum throughput
- Can tolerate some data loss
- Low latency is critical

---

### ⚡ `acks=1` (Leader Acknowledgment)

**Producer Acknowledgment:**
- ✅ Returns **after leader write completes**
- ⚠️ Does NOT wait for follower replication

**Leader Behavior:**
1. Writes message to local WAL
2. **Sends replication requests to ALL ISR followers asynchronously**
3. Responds to producer immediately after local write
4. Continues replication in background

**Follower Replication:**
- ✅ **All ISR followers receive replication requests**
- ⚠️ Non-ISR followers are excluded
- 🔄 Replication happens asynchronously after producer acknowledgment

**HWM Update:**
- 🔄 Updated **asynchronously** after `min.insync.replicas` acknowledge
- ⏰ May lag behind LEO slightly

**Use Case:**
- Balance between durability and performance
- Default Kafka behavior
- Acceptable for most use cases

---

### 🛡️ `acks=-1` (All ISR Replicas)

**Producer Acknowledgment:**
- ✅ Returns **ONLY after min.insync.replicas acknowledge**
- ✅ Guarantees durability

**Leader Behavior:**
1. Writes message to local WAL
2. **Sends replication requests to ALL ISR followers**
3. **Waits for min.insync.replicas to acknowledge**
4. Responds to producer only after sufficient acks

**Follower Replication:**
- ✅ **All ISR followers receive replication requests**
- ⚠️ Non-ISR followers are excluded
- ⏳ Leader waits for acknowledgments

**HWM Update:**
- ✅ Updated **synchronously** before responding to producer
- ✅ Advanced to LEO when min.insync.replicas acknowledge
- 🔒 Ensures committed messages are durable

**Use Case:**
- Maximum durability
- Critical data (financial transactions, user data)
- Can tolerate higher latency

---

## Important Implementation Details

### 1. ISR vs Non-ISR Followers

```
Example Partition Metadata:
- Leader: Broker 3
- Followers: [Broker 1, Broker 2]
- ISR: [Broker 3, Broker 2]

Replication Behavior:
✅ Broker 2: Receives replication (in ISR)
❌ Broker 1: Does NOT receive replication (not in ISR)
```

**Why this matters:**
- Only ISR members are trusted to be "caught up"
- Non-ISR followers must catch up through fetch requests
- This prevents slow followers from blocking writes

### 2. Min In-Sync Replicas

Configured via `min.insync.replicas` setting:
- **Default**: 1 (leader only)
- **Recommended**: 2 (leader + 1 follower)
- **Purpose**: Minimum replicas needed to consider write successful

**Impact on HWM:**
- HWM advances ONLY when this minimum is met
- For `acks=-1`: Producer fails if ISR < min.insync.replicas
- For `acks=0,1`: HWM updates asynchronously when condition is met

### 3. Replication Timeline

#### For `acks=0`:
```
Time   | Leader             | Follower        | Producer
-------|--------------------|-----------------|-----------------
T0     | Receives request   |                 | Sends request
T0+1ms |                    |                 | ✅ Receives success
T0+5ms | Writes to WAL      |                 |
T0+10ms| Sends replication  | Receives req    |
T0+15ms|                    | Writes to WAL   |
T0+20ms| Updates HWM        |                 |
```

#### For `acks=1`:
```
Time   | Leader             | Follower        | Producer
-------|--------------------|-----------------|-----------------
T0     | Receives request   |                 | Sends request
T0+5ms | Writes to WAL      |                 |
T0+6ms |                    |                 | ✅ Receives success
T0+10ms| Sends replication  | Receives req    |
T0+15ms|                    | Writes to WAL   |
T0+20ms| Updates HWM        |                 |
```

#### For `acks=-1`:
```
Time   | Leader             | Follower        | Producer
-------|--------------------|-----------------|-----------------
T0     | Receives request   |                 | Sends request
T0+5ms | Writes to WAL      |                 |
T0+10ms| Sends replication  | Receives req    |
T0+15ms|                    | Writes to WAL   |
T0+16ms|                    | Sends ack       |
T0+17ms| Updates HWM        |                 |
T0+18ms|                    |                 | ✅ Receives success
```

### 4. Code Flow

```java
// acks=0 or acks=1
1. Leader writes to WAL
2. Leader responds to producer (acks=1 only)
3. Leader sends async replication to ALL ISR followers
4. Background task waits for min.insync.replicas acks
5. Background task updates HWM when condition is met

// acks=-1
1. Leader writes to WAL
2. Leader sends replication to ALL ISR followers (synchronous)
3. Leader waits for min.insync.replicas acks
4. Leader updates HWM
5. Leader responds to producer
```

## Configuration

### Storage Service Configuration
```yaml
replication:
  min-insync-replicas: 2     # Minimum replicas for HWM advancement
  fetch-max-wait-ms: 5000    # Replication timeout
  fetch-min-bytes: 1         # Minimum bytes to fetch
```

### Metadata Configuration
```json
{
  "partitions": [
    {
      "topic": "orders",
      "partition": 0,
      "leaderId": 1,
      "followerIds": [2, 3],
      "isrIds": [1, 2],        // Only these receive replication
      "leaderEpoch": 1
    }
  ]
}
```

## Monitoring and Debugging

### Log Messages to Watch

**For Replication Requests:**
```
INFO: Starting replication for topic-partition orders-0 with 1 messages, baseOffset: 10, requiredAcks: -1
DEBUG: Sending replication request to follower 2: http://localhost:8082/api/v1/storage/replicate
INFO: Replication completed for orders-0: 1/1 followers acknowledged successfully
```

**For HWM Updates:**
```
DEBUG: Updated High Watermark to 11 for topic-partition: orders-0 (ISR replicas: 1/1)
DEBUG: Async HWM update: orders-0 advanced to 11
```

**For ISR Status:**
```
INFO: Updated leadership for orders-0: leader=1, followers=[2, 3], isr=[1, 2], epoch=1
```

### Common Issues

#### Issue: Only 1 follower receives replication (out of 2 followers)
**Cause:** The other follower is NOT in ISR
**Solution:** 
- Check ISR status in metadata
- Follower may be lagging or down
- Non-ISR followers must catch up through fetch requests

#### Issue: HWM not advancing for acks=0 or acks=1
**Cause:** Async replication may be failing or slow
**Solution:**
- Check follower logs for replication errors
- Verify network connectivity between brokers
- Check if min.insync.replicas requirement is met

#### Issue: Producer timeout with acks=-1
**Cause:** Not enough ISR replicas to meet min.insync.replicas
**Solution:**
- Check ISR membership
- Reduce min.insync.replicas (not recommended for production)
- Bring down followers back online

## Testing Replication

Use the provided test script:
```bash
python test/test-scripts/storage_api_tester.py --url http://localhost:8081
```

Check the generated `storage_service_api_responses.json` for:
- Produce response offsets
- High water marks
- Replication timing

## Summary

| Aspect | acks=0 | acks=1 | acks=-1 |
|--------|--------|--------|---------|
| **Replicates to ISR?** | ✅ Yes (async) | ✅ Yes (async) | ✅ Yes (sync) |
| **Producer Response** | Immediate | After leader write | After ISR acks |
| **HWM Update** | Async | Async | Sync |
| **Durability** | Low | Medium | High |
| **Latency** | Lowest | Low | Highest |
| **Throughput** | Highest | High | Lower |

**Key Takeaway:** All ack types replicate to ALL ISR followers. The difference is when the producer gets acknowledgment and when HWM is updated.
