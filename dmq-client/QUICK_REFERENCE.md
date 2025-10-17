# Quick Reference - Consumer Client Library Phase 1

## ✅ Your Scope: Client Library ONLY

**What YOU implement:**
- ✅ Consumer client library (DMQConsumer)
- ✅ HTTP client to call CES
- ✅ Local state management
- ✅ Polling from storage nodes
- ✅ Client-side partition assignment (Phase 2)

**What SOMEONE ELSE implements:**
- ⏳ Consumer Egress Service (CES)
- ⏳ CES endpoint: POST /api/consumer/join-group
- ⏳ Storage Service fetch endpoint

**You just need to call the CES endpoint - not implement it!**

---

## 🔌 API Contract

### CES Endpoint: `POST /api/consumer/join-group`

**Request:**
```json
{
  "groupId": "my-group",
  "consumerId": "consumer-abc123",
  "topics": ["orders"]
}
```

**Response (CES must provide):**
```json
{
  "success": true,
  "groupId": "my-group",
  "partitions": [
    {
      "topic": "orders",
      "partition": 0,
      "leader": {"brokerId": 1, "host": "storage-1", "port": 9092},
      "currentOffset": 100,
      "highWaterMark": 250,
      "isr": [1, 2, 3]
    }
  ]
}
```

### CES Must Provide Per Partition:
1. `topic` - Topic name
2. `partition` - Partition number
3. `leader` - Broker node (id, host, port)
4. `currentOffset` - Offset to start consuming from (CES decides: committed OR earliest)
5. `highWaterMark` - Latest offset available
6. `isr` - In-Sync Replica broker IDs

**Note:** CES does NOT track consumer group members! Client library handles all member logic locally.

---

## 💻 Client Usage

```java
// Configure
ConsumerConfig config = ConsumerConfig.builder()
    .metadataServiceUrl("http://localhost:8081")
    .groupId("my-group")
    .build();

// Create
Consumer consumer = new DMQConsumer(config);

// Subscribe (calls CES)
consumer.subscribe(Arrays.asList("orders"));

// Poll (calls Storage Service)
List<Message> messages = consumer.poll(1000);

// Process
for (Message msg : messages) {
    System.out.println(new String(msg.getValue()));
}

// Close
consumer.close();
```

---

## 🎯 Phase 1 vs Phase 2

| Feature | Phase 1 | Phase 2 |
|---------|---------|---------|
| Consumer group | ✅ Single member | ⏳ Multi-member |
| Join group | ✅ Via CES | ⏳ With rebalancing |
| Poll messages | ✅ All partitions | ⏳ Assigned partitions |
| Offset commit | ❌ Not implemented | ⏳ To CES |
| Heartbeat | ❌ Not needed | ⏳ Background thread |
| Rebalancing | ❌ Not needed | ⏳ Client-side logic |

---

## 📁 Key Files

**Client Library:**
- `DMQConsumer.java` - Main implementation
- `ConsumerEgressClient.java` - HTTP client for CES
- `ConsumerSubscriptionRequest/Response.java` - DTOs
- `PartitionMetadata.java` - Partition info model

**Documentation:**
- `PHASE1_IMPLEMENTATION_SUMMARY.md` - Complete details
- `QUICK_REFERENCE.md` - This file

---

## 🚀 Testing

**Unit Test Example:**
```java
@Test
public void testConsumerSubscribe() {
    // Mock CES response
    ConsumerSubscriptionResponse mockResponse = ConsumerSubscriptionResponse.builder()
        .success(true)
        .groupId("test-group")
        .partitions(List.of(
            PartitionMetadata.builder()
                .topic("test-topic")
                .partition(0)
                .leader(BrokerNode.builder()
                    .host("localhost")
                    .port(9092)
                    .build())
                .currentOffset(0L)
                .highWaterMark(100L)
                .isr(List.of(1, 2, 3))
                .build()
        ))
        .build();
    
    // Mock HTTP client
    when(egressClient.joinGroup(any())).thenReturn(mockResponse);
    
    // Test subscribe
    consumer.subscribe(List.of("test-topic"));
    
    // Verify
    assertEquals(1, consumer.getPartitionMetadata().size());
}
```

**Integration Test:**
1. Start CES with mock endpoint
2. Create consumer
3. Subscribe to topic
4. Verify HTTP call made
5. Verify partition metadata stored

---

## ⚠️ Important Notes

1. **CES decides offset**: Client doesn't implement offset reset policy - CES returns the correct `currentOffset` (committed or earliest)

2. **No commit in Phase 1**: Offset commit deferred to Phase 2

3. **No rebalancing**: Single consumer gets all partitions

4. **Future-proof**: All Phase 2 fields kept in DTOs but unused

---

## 🐛 Troubleshooting

**Consumer can't join group:**
- Check CES is running at configured URL
- Verify `/api/consumer/join-group` endpoint exists
- Check logs for HTTP errors

**No messages returned:**
- Verify Storage Service is running
- Check partition leaders are accessible
- Verify offset is correct (not past high watermark)

**Consumer hangs:**
- Check poll timeout setting
- Verify network connectivity to storage nodes
- Check for exceptions in logs

---

**Status: Phase 1 Complete ✅**  
**Ready for CES integration!** 🎉
