# Kafka-Clone Testing Suite

This directory contains all the necessary files to test the publish/replication flow of Kafka-Clone.

## 📁 Files Overview

### JSON Test Data
- **`metadata-setup.json`** - Metadata configuration for 3-broker cluster
- **`single-message-publish.json`** - Single message publish request
- **`batch-message-publish.json`** - Batch message publish request
- **`postman-collection.json`** - Postman collection for manual testing

### Scripts
- **`test-scripts/setup-and-test.sh`** - Automated setup and testing script
- **`test-scripts/health-check.sh`** - Health check for all brokers

## 🚀 Quick Start Testing

### Prerequisites
- Java 11+
- Maven 3.6+
- curl and jq (for automated scripts)
- 3 available ports (8081, 8082, 8083)

### Step 1: Start Broker Instances

Open 3 terminal windows and start each broker:

```bash
# Terminal 1 - Broker 1 (Leader for test-topic-0)
cd dmq-storage-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --app.broker.id=1"

# Terminal 2 - Broker 2 (Follower for test-topic-0)
cd dmq-storage-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8082 --app.broker.id=2"

# Terminal 3 - Broker 3 (Follower for test-topic-0)
cd dmq-storage-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8083 --app.broker.id=3"
```

### Step 2: Run Automated Tests

```bash
# Make scripts executable
chmod +x test/test-scripts/*.sh

# Run health check
./test/test-scripts/health-check.sh

# Run full setup and test
./test/test-scripts/setup-and-test.sh
```

### Step 3: Manual Testing with Postman

1. Import `postman-collection.json` into Postman
2. Run requests in order:
   - Health Check → All brokers healthy
   - Metadata Setup → Populate all brokers
   - Message Publishing → Test single/batch publish
   - Verification → Check high water marks

## 🧪 Test Scenarios

### Single Message Publish
- **Endpoint**: `POST http://localhost:8081/api/v1/storage/messages`
- **Data**: `single-message-publish.json`
- **Expected**: Message replicated to brokers 2 & 3, success response with offset

### Batch Message Publish
- **Endpoint**: `POST http://localhost:8081/api/v1/storage/messages`
- **Data**: `batch-message-publish.json`
- **Expected**: All 3 messages batched, replicated, sequential offsets returned

### Replication Verification
- **Check Logs**: Look for "Replication completed" messages
- **Check HWMs**: All brokers should have same high water mark
- **Check WALs**: Messages should exist in all broker data directories

## 🔍 Troubleshooting

### Common Issues

1. **Port Already in Use**
   ```bash
   # Check what's using the port
   netstat -ano | findstr :8081
   # Kill the process
   taskkill /PID <PID> /F
   ```

2. **Metadata Not Populated**
   ```bash
   # Manual metadata setup
   curl -X POST http://localhost:8081/api/v1/storage/metadata \
        -H "Content-Type: application/json" \
        -d @test/metadata-setup.json
   ```

3. **Replication Fails**
   - Check broker logs for network errors
   - Verify all brokers are healthy
   - Ensure metadata is populated on all brokers

### Debug Commands

```bash
# Check broker health
curl http://localhost:8081/api/v1/storage/health

# Check high water mark
curl http://localhost:8081/api/v1/storage/partitions/test-topic/0/high-water-mark

# View broker logs (in each terminal)
# Logs will show replication attempts and results
```

## 📊 Expected Test Results

### Successful Test Output
```
🚀 Kafka-Clone Testing Setup
=============================
🔍 Checking port availability...
✅ Port 8081 is available
✅ Port 8082 is available
✅ Port 8083 is available

🔄 Testing broker connectivity...
✅ Service is ready at http://localhost:8081
✅ Service is ready at http://localhost:8082
✅ Service is ready at http://localhost:8083

📤 Populating metadata on all brokers...
✅ Metadata populated for broker on port 8081
✅ Metadata populated for broker on port 8082
✅ Metadata populated for broker on port 8083

🧪 Running publish tests...
==========================
Testing single message publish...
✅ Publish successful on port 8081
   Response: 0
Testing batch message publish...
✅ Publish successful on port 8081
   Response: 1
```

### Log Verification
Check broker logs for messages like:
```
INFO  - Starting replication for topic-partition test-topic-0 with 1 messages
INFO  - Replication completed for test-topic-0: 2/2 ISR acknowledged successfully
INFO  - Appended message at offset: 0
INFO  - Updated High Watermark to 1 for topic-partition: test-topic-0
```

## 🎯 Next Steps

Once WAL.read() is implemented, you can test the complete flow:
1. Publish messages (above tests)
2. Consume messages from any broker
3. Verify data consistency across replicas

## 📝 Notes

- Base64 encoded values in JSON: "Hello World" = "SGVsbG8gV29ybGQ="
- All brokers should show same high water mark after replication
- Leader (Broker 1) handles all writes, followers receive replication
- ISR list determines which brokers receive replication requests