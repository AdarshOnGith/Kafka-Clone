# DMQ Kafka Clone - Quick Reference

## 🚀 Quick Start Commands

```bash
# Build entire project
mvn clean install

# Run Metadata Service (Terminal 1)
cd dmq-metadata-service && mvn spring-boot:run

# Run Storage Service (Terminal 2)
cd dmq-storage-service && mvn spring-boot:run

# Create a topic
curl -X POST http://localhost:8081/api/v1/metadata/topics \
  -H "Content-Type: application/json" \
  -d '{"topicName":"orders","partitionCount":3,"replicationFactor":2}'
```

## 📍 Service Endpoints

| Service | HTTP Port | Description |
|---------|-----------|-------------|
| Metadata Service | 8081 | Topic management, cluster coordination |
| Storage Service (Broker 1) | 8082 | Message storage and retrieval |
| ZooKeeper | 2181 | Cluster coordination |

## 🗂️ Module Overview

| Module | Purpose | Key Classes |
|--------|---------|-------------|
| **dmq-common** | Shared utilities | Message, TopicMetadata, PartitionUtil |
| **dmq-client** | Producer/Consumer | DMQProducer, DMQConsumer |
| **dmq-metadata-service** | Metadata & Controller | MetadataService, ControllerService |
| **dmq-storage-service** | Storage & Replication | WriteAheadLog, ReplicationManager |

## 🎯 Implementation Status

| Feature | Status | Location |
|---------|--------|----------|
| Project Structure | ✅ Complete | All modules |
| Common Models | ✅ Complete | dmq-common/model |
| REST Controllers | ⚠️ Boilerplate Only | */controller |
| Service Interfaces | ✅ Complete | */service |
| Service Implementations | ⚠️ Placeholder/TODO | */service/*Impl |
| Entity Classes | ⚠️ Placeholder/TODO | metadata/entity |
| WAL Structure | ⚠️ Placeholder/TODO | storage/wal |
| Producer Client | ⚠️ Placeholder/TODO | client/producer |
| Consumer Client | ⚠️ Placeholder/TODO | client/consumer |
| Metadata Operations | ❌ All TODO | metadata/service |
| Replication | ❌ All TODO | storage/service |
| ZooKeeper Integration | ❌ All TODO | metadata/coordination |
| Leader Election | ❌ All TODO | metadata/service |
| Consumer Groups | ❌ All TODO | client/consumer |

**Legend**: 
- ✅ Complete - Fully implemented
- ⚠️ Boilerplate/Placeholder - Structure exists, logic is TODO
- ❌ All TODO - Completely marked for implementation

**Note**: This is a learning scaffold. All business logic is intentionally left as TODO for you to implement and learn distributed systems concepts.

## 📋 Key TODO Items

### Critical - Core Structure (Already Done ✅)
- ✅ Maven multi-module setup
- ✅ REST endpoint definitions
- ✅ Service interface contracts
- ✅ JPA entity structure
- ✅ Configuration files

### High Priority - Business Logic (All TODO ❌)
1. ☐ Implement metadata service operations (create, read, update, delete topics)
2. ☐ Implement controller partition assignment logic
3. ☐ Complete WAL read/write operations
4. ☐ Implement producer send logic
5. ☐ Implement consumer poll logic
6. ☐ Add ZooKeeper broker registration

### Medium Priority - Advanced Features (All TODO ❌)
7. ☐ Implement replication protocol
8. ☐ Add heartbeat monitoring
9. ☐ Implement partition leader election
10. ☐ Add ISR management
11. ☐ Implement consumer group coordination

### Low Priority - Optimizations (All TODO ❌)
12. ☐ Add message compression
13. ☐ Implement log compaction
14. ☐ Add SSL/TLS support
15. ☐ Implement transactions

**Note**: The structure is 100% complete. All functional logic is intentionally left as TODO for learning purposes.

## 🔧 Configuration Files

### Metadata Service (`dmq-metadata-service/src/main/resources/application.yml`)
```yaml
server:
  port: 8081
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dmq_metadata
zookeeper:
  connect-string: localhost:2181
```

### Storage Service (`dmq-storage-service/src/main/resources/application.yml`)
```yaml
server:
  port: 8082
broker:
  id: 1
  data-dir: ./data/broker-1
metadata:
  service-url: http://localhost:8081
```

## 🧪 Test Commands

**Note**: These will return placeholder responses until you implement the business logic.

```bash
# Health check (Spring Boot actuator - works)
curl http://localhost:8081/actuator/health

# Create topic (returns empty placeholder)
curl -X POST http://localhost:8081/api/v1/metadata/topics \
  -H "Content-Type: application/json" \
  -d '{"topicName":"orders","partitionCount":3,"replicationFactor":2}'

# List topics (returns empty list until implemented)
curl http://localhost:8081/api/v1/metadata/topics

# Get topic metadata (returns empty placeholder)
curl http://localhost:8081/api/v1/metadata/topics/orders

# Produce message (placeholder response)
curl -X POST http://localhost:8082/api/v1/storage/produce \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "orders",
    "partition": 0,
    "key": "order123",
    "value": "SGVsbG8gV29ybGQh",
    "requiredAcks": 1
  }'

# Consume messages (returns empty list until implemented)
curl -X POST http://localhost:8082/api/v1/storage/consume \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "orders",
    "partition": 0,
    "offset": 0,
    "maxMessages": 10
  }'
```

## 📦 Maven Commands

```bash
# Build all modules
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run tests only
mvn test

# Run specific module
cd dmq-common && mvn clean install

# Update dependencies
mvn dependency:resolve

# Show dependency tree
mvn dependency:tree

# Clean all target directories
mvn clean

# Package as JAR
mvn package
```

## 🐛 Troubleshooting

| Problem | Solution |
|---------|----------|
| Port 8081 in use | Kill process: `lsof -i :8081` then `kill -9 <PID>` |
| PostgreSQL connection failed | Check `application.yml` credentials |
| ZooKeeper not connected | Verify ZooKeeper is running: `zkServer.sh status` |
| Build failed | Run `mvn clean install -U` |
| Class not found | Run `mvn clean install` in parent directory |

## 📚 File Locations

```
Kafka-Clone/
├── pom.xml                          # Parent POM
├── docs/
│   ├── PROJECT_STRUCTURE.md         # Complete structure
│   ├── GETTING_STARTED.md           # Setup guide
│   ├── SETUP_SUMMARY.md             # What was created
│   └── QUICK_REFERENCE.md           # This file
├── dmq-common/                      # Shared code
├── dmq-client/                      # Client library
├── dmq-metadata-service/            # Metadata service
└── dmq-storage-service/             # Storage service
```

## 🎓 Key Concepts

| Concept | Description |
|---------|-------------|
| **Topic** | Logical channel for messages |
| **Partition** | Ordered, immutable sequence of messages |
| **Broker** | Storage node that hosts partitions |
| **Leader** | Broker responsible for reads/writes to a partition |
| **Follower** | Replica broker that syncs from leader |
| **ISR** | In-Sync Replicas that are caught up with leader |
| **Offset** | Unique identifier for message position |
| **Consumer Group** | Set of consumers sharing partition consumption |
| **WAL** | Write-Ahead Log for durable storage |

## 🔗 Useful Links

- **Apache Kafka Docs**: https://kafka.apache.org/documentation/
- **Spring Boot Docs**: https://spring.io/projects/spring-boot
- **Apache Curator**: https://curator.apache.org/
- **Netty Guide**: https://netty.io/wiki/user-guide.html

## 💡 Tips

1. **Start Simple**: The project structure is ready - focus on implementing one feature at a time
2. **Follow TODOs**: Search for `// TODO:` comments - they guide you on what to implement
3. **Test Incrementally**: Each TODO is a small, testable piece
4. **Read Logs**: Enable DEBUG logging to see the placeholder flow
5. **Check Examples**: Look at Apache Kafka source code for implementation ideas
6. **Boilerplate is Done**: All class structures, dependencies, and configurations are ready

**Key Principle**: This is a learning scaffold. The architecture is production-grade, but the implementation is intentionally minimal so you learn by doing.

---

**Last Updated**: October 2025
**Version**: 1.0.0-SNAPSHOT
