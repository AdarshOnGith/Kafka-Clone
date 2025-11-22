# DMQ Metadata Service Test Scripts

This directory contains comprehensive test scripts for validating the DMQ Metadata Service implementation, including KRaft consensus, service discovery, metadata synchronization, and heartbeat processing.

## 📁 Directory Structure

```
test/metadata-service-related/
├── test-scripts/                    # Executable test scripts
│   ├── run_all_tests.sh            # Master test runner
│   ├── test_basic_metadata_operations.sh
│   ├── test_broker_registration.sh
│   ├── test_heartbeat_processing.sh
│   ├── test_metadata_synchronization.sh
│   └── test_kraft_consensus.sh
└── test-data/                      # Test data files
    └── test_payloads.json          # Sample payloads for testing
```

## 🚀 Quick Start

### Prerequisites
- Metadata service running on `http://localhost:9091`
- PostgreSQL database accessible
- `config/services.json` properly configured
- Storage services running on ports 8081, 8082, 8083

### Run All Tests
```bash
cd test/metadata-service-related/test-scripts
chmod +x *.sh
./run_all_tests.sh
```

### Run Individual Tests
```bash
# Test basic metadata operations
./test_basic_metadata_operations.sh

# Test broker registration
./test_broker_registration.sh

# Test heartbeat processing
./test_heartbeat_processing.sh

# Test metadata synchronization
./test_metadata_synchronization.sh

# Test KRaft consensus
./test_kraft_consensus.sh
```

## 🧪 Test Coverage

### 1. Basic Metadata Operations (`test_basic_metadata_operations.sh`)
**Tests**: Topic CRUD operations, broker registration, basic retrieval
- ✅ Broker registration (valid/invalid)
- ✅ Topic creation with validation
- ✅ Topic listing and retrieval
- ✅ Error handling for invalid inputs

### 2. Broker Registration (`test_broker_registration.sh`)
**Tests**: Broker management, registration validation, assignments
- ✅ Multiple broker registration
- ✅ Broker listing and retrieval
- ✅ Duplicate ID prevention
- ✅ Topic creation with broker assignments

### 3. Heartbeat Processing (`test_heartbeat_processing.sh`)
**Tests**: Heartbeat reception, lag detection, push sync triggers
- ✅ Heartbeat reception and validation
- ✅ Version comparison and lag detection
- ✅ Push synchronization triggering
- ✅ Multi-service heartbeat handling

### 4. Metadata Synchronization (`test_metadata_synchronization.sh`)
**Tests**: Push sync, version ordering, metadata consistency
- ✅ Push synchronization triggering
- ✅ Version ordering guarantees
- ✅ Metadata consistency across operations
- ✅ Incremental sync updates

### 5. KRaft Consensus (`test_kraft_consensus.sh`)
**Tests**: Raft consensus, leader election, log replication
- ✅ Consensus-based operations
- ✅ Log replication and commits
- ✅ Leader election handling
- ✅ State consistency

## 📊 Test Results

Each test script provides:
- **Colored output** for easy reading
- **Detailed logging** of each operation
- **Success/failure indicators**
- **Expected log messages** to verify in service logs
- **Post-test verification steps**

## 🔍 Expected Log Messages

### Metadata Service Logs
```
INFO  - KRaft node initialized as leader
INFO  - Received heartbeat from storage service storage-1
INFO  - Storage service storage-1 is behind, triggering push sync
INFO  - Successfully sent metadata update to storage service
INFO  - Committed entry to Raft log: [operation]
INFO  - Applied committed entry: [operation]
```

### Storage Service Logs (when running)
```
INFO  - Successfully sent heartbeat to controller
INFO  - Received metadata update from controller
INFO  - Updated local metadata version to: [timestamp]
INFO  - Metadata sync completed successfully
```

## 🛠️ Troubleshooting

### Common Issues

**"Metadata service is not accessible"**
```bash
# Start metadata service
cd dmq-metadata-service
mvn spring-boot:run
```

**"Port 9091 already in use"**
```bash
# Find process using port
netstat -ano | findstr :9091
# Kill the process
taskkill /PID <PID> /F
```

**"Database connection failed"**
- Verify PostgreSQL is running
- Check `application.yml` database configuration
- Ensure database exists: `createdb dmq_metadata`

**Tests failing with validation errors**
- Check `config/services.json` configuration
- Verify service URLs are correct
- Ensure all required fields are present

### Debug Mode
Enable debug logging in `application.yml`:
```yaml
logging:
  level:
    com.distributedmq: DEBUG
```

## 📈 Performance Testing

For load testing, modify the scripts to run operations in loops:

```bash
# Example: Create 100 topics
for i in {1..100}; do
  curl -X POST http://localhost:9091/api/v1/metadata/topics \
    -H "Content-Type: application/json" \
    -d "{\"topicName\":\"load-test-topic-$i\",\"partitionCount\":1,\"replicationFactor\":1}"
done
```

## 🔄 Integration Testing

To test full integration with storage service:

1. Start metadata service
2. Start storage service
3. Run metadata tests
4. Check cross-service synchronization
5. Test producer/consumer flows

## 📋 Test Data

The `test-data/test_payloads.json` file contains sample data for:
- Valid/invalid broker registrations
- Valid/invalid topic creations
- Valid/invalid heartbeats
- Bulk operations for load testing

## 🎯 Success Criteria

**All tests pass** when:
- ✅ HTTP responses are successful (200/201)
- ✅ Expected log messages appear
- ✅ Metadata persists in database
- ✅ KRaft consensus operates correctly
- ✅ Heartbeat sync works bidirectionally
- ✅ No exceptions in service logs

## 📝 Manual Testing

For manual testing without scripts:

```bash
# Register broker
curl -X POST http://localhost:9091/api/v1/metadata/brokers \
  -H "Content-Type: application/json" \
  -d '{"id": 101, "host": "localhost", "port": 8081, "rack": "rack1"}'

# Create topic
curl -X POST http://localhost:9091/api/v1/metadata/topics \
  -H "Content-Type: application/json" \
  -d '{"topicName": "manual-test", "partitionCount": 2, "replicationFactor": 1}'

# Check topics
curl http://localhost:9091/api/v1/metadata/topics
```

## 🤝 Contributing

When adding new tests:
1. Follow the existing script structure
2. Include both positive and negative test cases
3. Add expected log message verification
4. Update this README
5. Test with both single and integrated service setups

---

**Last Updated**: October 2025
**Test Coverage**: KRaft Consensus, Metadata Sync, Heartbeat Processing
**Status**: Ready for execution