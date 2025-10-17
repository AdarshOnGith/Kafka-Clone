# Storage Service Request Format

## Summary of Changes

Updated the `StorageServiceClient.java` to match your friend's storage service expected format.

## Your Friend's Expected Format

```json
{
  "topic": "test-topic",
  "partition": 0,
  "messages": [
    {
      "key": "user123",
      "value": "SGVsbG8gV29ybGQ=",
      "timestamp": 1703123456789
    }
  ],
  "producerId": "producer-1",
  "producerEpoch": 1,
  "requiredAcks": -1,
  "timeoutMs": 5000
}
```

## Changes Made

### 1. Added Producer Identification
- **producerId**: Now set to `"producer-1"`
- **producerEpoch**: Now set to `1`

### 2. Updated Acknowledgment Settings
- **requiredAcks**: Changed from `1` → `-1` (wait for all in-sync replicas)
- **timeoutMs**: Changed from `30000L` → `5000L` (5 seconds to match friend's cluster)

### 3. Message Value Encoding
- **No changes needed** - Jackson automatically converts `byte[]` to Base64 string
- Example: `"Hello World"` → `"SGVsbG8gV29ybGQ="`

## Updated Code in StorageServiceClient.java

```java
ProduceRequest request = ProduceRequest.builder()
    .topic(topic)
    .partition(partition)
    .messages(List.of(message))
    .producerId("producer-1")          // ✅ Added
    .producerEpoch(1)                  // ✅ Added
    .requiredAcks(-1)                  // ✅ Changed from 1
    .timeoutMs(5000L)                  // ✅ Changed from 30000L
    .build();
```

## Testing

The JSON request will now match exactly what your friend's Python test function expects:

```python
def test_publish(port, test_file):
    url = f"http://localhost:{port}/api/v1/storage/messages"
    response = requests.post(url, json=data, timeout=10)
    # Will now receive correct format! ✅
```

## Expected Response from Friend's Storage Service

```json
{
  "success": true,
  "results": [
    {
      "offset": 123,
      "partition": 0,
      "topic": "test-topic"
    }
  ]
}
```

## Next Steps

1. ✅ Format updated to match friend's expectation
2. ⏳ Wait for friend to start storage services on ports 8082, 8083
3. ⏳ Test end-to-end message sending
4. ⏳ Verify offset assignment from storage service

## Debugging

Added log statement to see the exact JSON being sent:
```java
log.info("📤 Sending JSON to storage service:\n{}", requestJson);
```

This will print the exact JSON in the console when you send a message.
