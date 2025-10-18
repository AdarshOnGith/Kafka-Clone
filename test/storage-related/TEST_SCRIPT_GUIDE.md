# Test Script Guide - Replication Behavior Testing

## Overview
The `setup-and-test.py` script has been updated to comprehensively test the replication behavior for different acknowledgment (`acks`) settings in the Kafka-Clone storage service.

## Location
- **Script**: `test/test-scripts/setup-and-test.py`
- **Response File**: `test/test_responses.txt` (auto-generated)

## What the Script Tests

### 1. Replication Behavior by Acks Type

#### Test 1: `acks=0` (Fire-and-Forget)
- **Topic**: `orders`
- **Partition**: 0
- **Leader Port**: 8082
- **Expected Behavior**:
  - Returns immediately (doesn't wait for leader write)
  - Replicates to ALL ISR followers asynchronously
  - HWM updated asynchronously after min.insync.replicas ack
  - **Fastest response time**

#### Test 2: `acks=1` (Leader Acknowledgment)
- **Topic**: `orders`
- **Partition**: 0
- **Leader Port**: 8082
- **Expected Behavior**:
  - Returns after leader write completes
  - Replicates to ALL ISR followers asynchronously
  - HWM updated asynchronously after min.insync.replicas ack
  - **Medium response time**

#### Test 3: `acks=-1` (All ISR Replicas)
- **Topic**: `orders`
- **Partition**: 1
- **Leader Port**: 8083
- **Expected Behavior**:
  - Replicates to ALL ISR followers synchronously
  - Returns ONLY after min.insync.replicas acknowledge
  - HWM updated synchronously before response
  - **Slowest response time** (but most durable)

#### Test 4: High Water Mark Verification
- Checks HWM for both `orders-0` and `orders-1`
- Verifies HWM advancement based on replication

## Prerequisites

### 1. Start All 3 Brokers
You need to have all 3 storage service brokers running:

**Terminal 1 (Broker 1):**
```powershell
cd c:\Users\123ad\Downloads\Distri_Kafka\Kafka-Clone\dmq-storage-service
$env:BROKER_ID = "1"; $env:SERVER_PORT = "8081"; mvn spring-boot:run
```

**Terminal 2 (Broker 2):**
```powershell
cd c:\Users\123ad\Downloads\Distri_Kafka\Kafka-Clone\dmq-storage-service
$env:BROKER_ID = "2"; $env:SERVER_PORT = "8082"; mvn spring-boot:run
```

**Terminal 3 (Broker 3):**
```powershell
cd c:\Users\123ad\Downloads\Distri_Kafka\Kafka-Clone\dmq-storage-service
$env:BROKER_ID = "3"; $env:SERVER_PORT = "8083"; mvn spring-boot:run
```

### 2. Metadata Configuration
The script uses `test/metadata-setup.json` which should have:
- **orders-0**: Leader=Broker 2 (port 8082), ISR=[2, 1]
- **orders-1**: Leader=Broker 3 (port 8083), ISR=[3, 2]

## Running the Test

### Execute the Script
```bash
python test/test-scripts/setup-and-test.py
```

### Interactive Flow
1. **Broker Detection**: Checks if all 3 brokers are running
2. **Metadata Population**: Sends metadata to all brokers
3. **Cluster Topology**: Displays partition leadership and ISR
4. **Replication Tests**: Runs all 4 test scenarios
5. **Response Logging**: Saves all HTTP requests/responses

## Output Files

### Console Output
The script provides rich console output with:
- ✅/❌ Success/failure indicators
- Response times in milliseconds
- Offset information
- HWM values
- Expected behavior explanations

### Response File: `test/test_responses.txt`

The file contains **complete HTTP request/response details** for each test:

```
================================================================================
TEST: Produce with acks=0 (orders-0)
Timestamp: 2025-10-18T01:23:45.123456
================================================================================

REQUEST:
----------------------------------------
method: POST
url: http://localhost:8082/api/v1/storage/messages
port: 8082
topic: orders
partition: 0
acks: 0
body:
{
  "topic": "orders",
  "partition": 0,
  "producerId": 10001,
  ...
}

RESPONSE:
----------------------------------------
status_code: 200
response_time_ms: 5.23
headers:
{
  "Content-Type": "application/json",
  ...
}
body:
{
  "topic": "orders",
  "partition": 0,
  "success": true,
  ...
}

================================================================================
```

## What to Look For

### 1. Response Times
Compare response times across acks settings:
- `acks=0`: Should be **fastest** (typically < 10ms)
- `acks=1`: Should be **medium** (typically 10-50ms)
- `acks=-1`: Should be **slowest** (typically 50-200ms depending on network)

### 2. Broker Logs
Check broker logs for replication activity:

**For acks=0 and acks=1:**
```
DEBUG: Message appended at offset: X (acks=0)
DEBUG: Async replication initiated for orders-0 at offset X (acks=0), success: true
DEBUG: Async HWM update: orders-0 advanced to Y
```

**For acks=-1:**
```
DEBUG: Message appended at offset: X (acks=-1)
INFO: Starting replication for topic-partition orders-1 with 1 messages, baseOffset: X, requiredAcks: -1
DEBUG: Sending replication request to follower 2: http://localhost:8082/api/v1/storage/replicate
INFO: Replication completed for orders-1: 1/1 followers acknowledged successfully
DEBUG: Updated High Watermark to Y for topic-partition: orders-1 (ISR replicas: 1/1)
```

### 3. ISR Behavior
Verify that:
- Only ISR members receive replication requests
- Non-ISR followers are excluded (as expected)
- Example: If ISR=[3, 2], only Broker 2 receives replication from Broker 3

### 4. HWM Advancement
Check that:
- **acks=-1**: HWM advances immediately (synchronous)
- **acks=0,1**: HWM may lag initially, advances asynchronously

## Troubleshooting

### Issue: "Broker not running" error
**Solution**: Start all 3 brokers as shown in Prerequisites

### Issue: "Failed to populate metadata"
**Solution**: 
- Check if metadata-setup.json exists in test/ directory
- Verify JSON is valid
- Check broker logs for errors

### Issue: Replication not happening
**Solution**:
- Verify ISR membership in metadata
- Check broker connectivity (network)
- Review broker logs for replication errors

### Issue: Response file not created
**Solution**:
- Check write permissions in test/ directory
- Verify Python has access to create files
- Check for exceptions in console output

## Key Observations from Test Results

### Expected Results

| Metric | acks=0 | acks=1 | acks=-1 |
|--------|--------|--------|---------|
| **Response Time** | Fastest | Medium | Slowest |
| **Replication** | Async to ISR | Async to ISR | Sync to ISR |
| **HWM Update** | Async | Async | Sync |
| **Durability** | Lowest | Medium | Highest |

### Sample Response Times
Based on typical local testing:
- `acks=0`: 3-8ms (immediate return)
- `acks=1`: 10-30ms (after leader write)
- `acks=-1`: 50-150ms (after ISR acks)

## Integration with Documentation

This test script validates the behavior documented in:
- `docs/REPLICATION_BEHAVIOR.md` - Detailed replication semantics
- Code comments in `StorageServiceImpl.java` - Implementation details

## Next Steps

After running the test:
1. **Review `test_responses.txt`** for complete HTTP details
2. **Check broker logs** for replication messages
3. **Compare response times** to validate async vs sync behavior
4. **Verify ISR-only replication** in logs
5. **Monitor HWM advancement** for each acks type

## Advanced Testing

To test edge cases:
1. **Remove a follower from ISR** - Verify it doesn't receive replication
2. **Set min.insync.replicas=2** - Test acks=-1 failure when ISR < min
3. **Add network delay** - Observe impact on acks=-1 response time
4. **Test with multiple messages** - Verify batch replication behavior

---

**Note**: All responses are captured exactly as received from the HTTP API, including headers, status codes, and body content. This provides a complete record for debugging and verification.
