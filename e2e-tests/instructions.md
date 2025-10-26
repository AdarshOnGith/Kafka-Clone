# End-to-End Testing Suite for DMQ Kafka Clone

This directory contains comprehensive end-to-end testing scripts for all implemented flows in the DMQ distributed messaging system.

## 📋 Prerequisites

- Java 11+ installed
- Maven 3.6+ installed
- curl or Postman for API testing
- Ports 8080 (metadata) and 8082 (storage) available
- Windows PowerShell (for .ps1 scripts) or Bash (for .sh scripts)

## 🏗️ Project Structure

```
e2e-tests/
├── scripts/
│   ├── setup/
│   │   ├── 01-setup-services.ps1      # Configure service mappings
│   │   ├── 02-start-services.ps1      # Start both services
│   │   ├── 03-init-test-data.ps1      # Initialize brokers/topics
│   │   └── cleanup.ps1                # Clean up test data
│   ├── storage/
│   │   ├── producer-flow-test.ps1     # Test message production
│   │   ├── consumer-flow-test.ps1     # Test message consumption
│   │   ├── wal-management-test.ps1    # Test WAL operations
│   │   └── partition-status-test.ps1  # Test partition reporting
│   ├── metadata/
│   │   ├── topic-management-test.ps1  # Test topic CRUD
│   │   ├── broker-management-test.ps1 # Test broker registration
│   │   ├── kraft-consensus-test.ps1   # Test Raft protocol
│   │   └── service-discovery-test.ps1 # Test service discovery
│   ├── integration/
│   │   ├── producer-e2e-test.ps1      # End-to-end producer flow
│   │   ├── consumer-e2e-test.ps1      # End-to-end consumer flow
│   │   ├── metadata-sync-test.ps1     # Metadata synchronization
│   │   ├── heartbeat-flow-test.ps1    # Heartbeat mechanisms
│   │   └── replication-flow-test.ps1  # Replication (multi-broker)
│   └── monitoring/
│       ├── health-check.ps1           # Service health validation
│       ├── log-monitor.ps1            # Log monitoring utility
│       └── metrics-collector.ps1      # Performance metrics
├── config/
│   └── services.json                  # Service configuration
├── data/
│   └── test-payloads.json             # Test data payloads
└── README.md                          # This file
```

## 🚀 Quick Start

### 1. Initial Setup
```powershell
# Navigate to e2e-tests directory
cd e2e-tests

# Run setup scripts in order
.\scripts\setup\01-setup-services.ps1
.\scripts\setup\02-start-services.ps1
.\scripts\setup\03-init-test-data.ps1
```

### 2. Run Individual Flow Tests
```powershell
# Test storage service flows
.\scripts\storage\producer-flow-test.ps1
.\scripts\storage\consumer-flow-test.ps1

# Test metadata service flows
.\scripts\metadata\topic-management-test.ps1
.\scripts\metadata\broker-management-test.ps1

# Test cross-service integration
.\scripts\integration\producer-e2e-test.ps1
.\scripts\integration\metadata-sync-test.ps1
```

### 3. Run All Tests
```powershell
# Run complete test suite
.\run-all-tests.ps1
```

## 📝 Detailed Test Scenarios

### Storage Service Independent Flows

#### 1. Message Production Flow
**Script:** `scripts/storage/producer-flow-test.ps1`
**Purpose:** Test batch message append with offset assignment
**Validation:**
- Single message production
- Batch message production
- Offset assignment (0, 1, 2...)
- Error handling for invalid requests

#### 2. Message Consumption Flow
**Script:** `scripts/storage/consumer-flow-test.ps1`
**Purpose:** Test message retrieval with offset-based reading
**Validation:**
- Read from offset 0 (all messages)
- Read from offset 1 (partial messages)
- Index-based fast seeks
- Message ordering and integrity

#### 3. WAL Management Flow
**Script:** `scripts/storage/wal-management-test.ps1`
**Purpose:** Test durable log operations
**Validation:**
- Segment file creation
- Index file generation
- Flush operations
- Log file persistence

#### 4. Partition Status Flow
**Script:** `scripts/storage/partition-status-test.ps1`
**Purpose:** Test partition metrics reporting
**Validation:**
- LEO (Log End Offset) tracking
- HWM (High Water Mark) updates
- Partition role identification

### Metadata Service Independent Flows

#### 1. Topic Management Flow
**Script:** `scripts/metadata/topic-management-test.ps1`
**Purpose:** Test topic CRUD operations
**Validation:**
- Topic creation with partitions
- Topic retrieval and listing
- Topic deletion
- Partition assignment logic

#### 2. Broker Management Flow
**Script:** `scripts/metadata/broker-management-test.ps1`
**Purpose:** Test broker registration and tracking
**Validation:**
- Broker registration
- Broker information retrieval
- Broker listing
- Status updates

#### 3. KRaft Consensus Flow
**Script:** `scripts/metadata/kraft-consensus-test.ps1`
**Purpose:** Test distributed consensus protocol
**Validation:**
- Leader election
- Log replication
- Controller status
- Raft state transitions

#### 4. Service Discovery Flow
**Script:** `scripts/metadata/service-discovery-test.ps1`
**Purpose:** Test service URL resolution
**Validation:**
- Service mapping loading
- URL resolution
- Service pairing logic

### Cross-Service Integration Flows

#### 1. End-to-End Producer Flow
**Script:** `scripts/integration/producer-e2e-test.ps1`
**Purpose:** Complete producer journey across services
**Flow:**
1. Metadata service provides topic info
2. Storage service validates leadership
3. Message appended to WAL
4. Metadata service receives heartbeat
5. Client receives acknowledgment

#### 2. End-to-End Consumer Flow
**Script:** `scripts/integration/consumer-e2e-test.ps1`
**Purpose:** Complete consumer journey across services
**Flow:**
1. Consumer requests messages
2. Storage service uses index for fast lookup
3. Messages read from WAL segments
4. Metadata service tracks partition status
5. Consumer receives message batch

#### 3. Metadata Synchronization Flow
**Script:** `scripts/integration/metadata-sync-test.ps1`
**Purpose:** Test bidirectional metadata sync
**Flow:**
1. Topic created in metadata service
2. Push sync sends update to storage service
3. Storage service updates local metadata
4. Heartbeat confirms sync status
5. Storage service can handle new topic

#### 4. Heartbeat Flow
**Script:** `scripts/integration/heartbeat-flow-test.ps1`
**Purpose:** Test heartbeat mechanisms
**Flow:**
1. Storage service sends periodic heartbeats
2. Metadata service processes heartbeat data
3. Version comparison for sync detection
4. Recovery triggers for outdated services
5. Status monitoring and alerting

#### 5. Replication Flow (Advanced)
**Script:** `scripts/integration/replication-flow-test.ps1`
**Purpose:** Test multi-broker replication
**Requirements:** Multiple storage service instances
**Flow:**
1. Setup multiple brokers
2. Create replicated topic
3. Send message with acks=all
4. Verify replication to followers
5. Check ISR status updates

## 🔧 Configuration

### Service Configuration
The `config/services.json` file defines service mappings:

```json
{
  "metadata-services": [
    {
      "id": 1,
      "host": "localhost",
      "port": 8080,
      "paired-storage": 1
    }
  ],
  "storage-services": [
    {
      "id": 1,
      "host": "localhost",
      "port": 8082,
      "paired-metadata": 1
    }
  ]
}
```

### Application Properties
Ensure these properties in respective `application.yml` files:

**Storage Service (`dmq-storage-service/src/main/resources/application.yml`):**
```yaml
server:
  port: 8082
broker:
  id: 1
metadata:
  service-url: http://localhost:8080
wal:
  segment-size-bytes: 1073741824
```

**Metadata Service (`dmq-metadata-service/src/main/resources/application.yml`):**
```yaml
server:
  port: 8080
metadata:
  cluster-id: "test-cluster"
raft:
  election-timeout-ms: 5000
```

## 📊 Monitoring and Validation

### Health Checks
```powershell
.\scripts\monitoring\health-check.ps1
```

### Log Monitoring
```powershell
.\scripts\monitoring\log-monitor.ps1
```

### Metrics Collection
```powershell
.\scripts\monitoring\metrics-collector.ps1
```

## 🧹 Cleanup

### Test Data Cleanup
```powershell
.\scripts\setup\cleanup.ps1
```

### Complete Environment Reset
```powershell
# Stop all services
Get-Process java | Stop-Process -Force

# Clean data directories
Remove-Item -Recurse -Force ..\dmq-storage-service\data\*
Remove-Item -Recurse -Force ..\dmq-metadata-service\data\*

# Reset configurations
git checkout ..\config\
```

## 🚨 Troubleshooting

### Common Issues

#### Port Conflicts
```powershell
# Check port usage
netstat -ano | findstr :8080
netstat -ano | findstr :8082

# Kill conflicting processes
Stop-Process -Id <PID>
```

#### Service Startup Failures
```powershell
# Check Java version
java -version

# Check Maven version
mvn -version

# Clean and rebuild
mvn clean compile
```

#### Test Failures
```powershell
# Check service logs
Get-Content ..\dmq-storage-service\logs\spring.log -Tail 50
Get-Content ..\dmq-metadata-service\logs\spring.log -Tail 50

# Verify service health
curl http://localhost:8082/api/v1/storage/health
curl http://localhost:8080/api/v1/metadata/health
```

### Debug Mode
Enable debug logging by adding to `application.yml`:
```yaml
logging:
  level:
    com.distributedmq: DEBUG
```

## 📈 Performance Testing

### Load Testing
```powershell
# Install Apache Bench (Windows)
choco install apache-httpd-tools

# Producer load test
ab -n 1000 -c 10 -T application/json -p data/test-payloads.json http://localhost:8082/api/v1/storage/messages

# Consumer load test
ab -n 1000 -c 10 -T application/json -p data/consumer-payload.json http://localhost:8082/api/v1/storage/consume
```

### Metrics Monitoring
Enable Spring Boot Actuator in `application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

Access metrics at:
- Storage: http://localhost:8082/actuator/metrics
- Metadata: http://localhost:8080/actuator/metrics

## 🔄 CI/CD Integration

### Automated Test Execution
```yaml
# .github/workflows/e2e-tests.yml
name: E2E Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '11'
          distribution: 'adopt'
      - name: Run E2E Tests
        run: |
          cd e2e-tests
          ./run-all-tests.ps1
```

## 📞 Support

For issues or questions:
1. Check the troubleshooting section above
2. Review service logs for error details
3. Verify configuration files are correct
4. Ensure all prerequisites are installed
5. Check GitHub issues for similar problems

## 📋 Test Checklist

- [ ] Services start successfully
- [ ] Broker registration works
- [ ] Topic creation succeeds
- [ ] Message production works
- [ ] Message consumption works
- [ ] Metadata sync functions
- [ ] Heartbeat mechanism active
- [ ] WAL operations durable
- [ ] Index files generated
- [ ] Partition status reported
- [ ] KRaft consensus operational
- [ ] Service discovery functional
- [ ] Cross-service flows complete
- [ ] Error handling robust
- [ ] Performance acceptable
- [ ] Cleanup successful