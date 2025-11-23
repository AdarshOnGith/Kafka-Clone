# DistributedMQ - Kafka Clone

[![Java](https://img.shields.io/badge/Java-11-orange.svg)](https://openjdk.java.net/projects/jdk/11/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A modern, cloud-native distributed messaging system inspired by Apache Kafka, built with Java 11, Spring Boot, and implementing the Raft consensus protocol (KRaft mode).

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Features](#features)
- [Quick Start](#quick-start)
- [Components](#components)
- [API Documentation](#api-documentation)
- [Configuration](#configuration)
- [Testing](#testing)
- [Documentation](#documentation)

## 🎯 Overview

DistributedMQ is a distributed messaging system that implements core Kafka-like functionality with modern cloud-native architecture. The system uses **Raft consensus protocol (KRaft mode)** for metadata management, eliminating the need for ZooKeeper, and provides reliable message storage with automatic failover and replication.

### Key Characteristics

- **Distributed**: Multi-node deployment with automatic leader election via Raft
- **Fault Tolerant**: Survives node failures with automatic controller failover
- **Scalable**: Horizontal scaling through broker addition
- **Cloud-Native**: Container-ready with external configuration (services.json)
- **Production Ready**: Comprehensive logging, monitoring, and error handling

## 🏗️ Architecture

The system consists of four main modules:

### Core Modules

1. **dmq-common** - Shared DTOs, utilities, and configuration
2. **dmq-client** - Producer/Consumer client library  
3. **dmq-metadata-service** - KRaft-based controller with Raft consensus (Ports: 9091, 9092, 9093)
4. **dmq-storage-service** - Broker nodes with partition replication (Ports: 8081-8085)

### System Topology

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Metadata Node 1 │    │ Metadata Node 2 │    │ Metadata Node 3 │
│ (Port: 9091)    │    │ (Port: 9092)    │    │ (Port: 9093)    │
│ Raft Follower   │◄──►│ Raft Leader     │◄──►│ Raft Follower   │
└────────┬────────┘    └────────┬────────┘    └────────┬────────┘
         │                      │                       │
         └──────────────────────┼───────────────────────┘
                                │
                   ┌────────────▼────────────┐
                   │    Controller (Leader)   │
                   │  Metadata Management     │
                   └────────────┬─────────────┘
                                │
                   ┌────────────┴────────────┐
                   │                         │
          ┌────────▼────────┐       ┌────────▼────────┐
          │ Storage Broker  │       │ Storage Broker  │
          │ ID: 101         │       │ ID: 102         │
          │ Port: 8081      │       │ Port: 8082      │
          └─────────────────┘       └─────────────────┘
```

### Communication Flow

1. **Controller Election**: Raft consensus elects leader among 3 metadata nodes
2. **Broker Discovery**: Storage nodes discover controller via parallel queries
3. **Broker Registration**: Brokers register with discovered controller
4. **Heartbeat**: Brokers send periodic heartbeats (5s interval) to controller
5. **Metadata Sync**: Brokers pull and maintain current cluster metadata
6. **Controller Failover**: Automatic switch to new controller on leader failure

## ✨ Features

### Core Messaging Features
- ✅ **Topics & Partitions**: Logical message streams with horizontal scaling
- ✅ **Message Persistence**: Durable storage with partition management
- ✅ **Producer API**: High-throughput message publishing
- ✅ **Consumer API**: Message consumption with offset management

### Distributed Systems Features
- ✅ **Raft Consensus (KRaft)**: Full implementation for metadata management
  - Leader election with randomized timeouts
  - Log replication with majority consensus
  - Persistent log storage with compaction
- ✅ **Automatic Controller Failover**: 
  - New leader election on controller failure (~5-10 seconds)
  - CONTROLLER_CHANGED push notifications to all brokers
  - Automatic broker reconnection to new controller
- ✅ **Broker Failover**: 
  - Health monitoring via heartbeat (30s timeout)
  - Automatic OFFLINE status marking
  - Partition reassignment on broker failure
- ✅ **Controller Discovery**:
  - Parallel queries to all metadata nodes
  - First successful response strategy
  - Retry with exponential backoff
- ✅ **Replication**: Configurable replication factor with ISR management
- ✅ **Load Balancing**: Automatic partition distribution across brokers

### Operational Features
- ✅ **Health Monitoring**: Heartbeat-based broker health tracking
- ✅ **Metrics & Observability**: Comprehensive emoji-based logging (🔍 📡 ✅ ❌ 🎖️ 🔄)
- ✅ **Configuration Management**: External services.json for service discovery
- ✅ **Container Ready**: Docker/Kubernetes deployment compatible

## 🚀 Quick Start

### Prerequisites

- Java 11 or higher
- Maven 3.6+
- PostgreSQL (optional, uses H2 by default)

### 1. Clone and Build

```bash
git clone https://github.com/AdarshOnGith/Kafka-Clone.git
cd Kafka-Clone
mvn clean install
```

### 2. Configure Service Discovery

Edit `config/services.json`:
```json
{
  "services": {
    "metadata-services": [
      {"id": 1, "host": "localhost", "port": 9091, "url": "http://localhost:9091"},
      {"id": 2, "host": "localhost", "port": 9092, "url": "http://localhost:9092"},
      {"id": 3, "host": "localhost", "port": 9093, "url": "http://localhost:9093"}
    ],
    "storage-services": [
      {"id": 101, "host": "localhost", "port": 8081, "url": "http://localhost:8081"},
      {"id": 102, "host": "localhost", "port": 8082, "url": "http://localhost:8082"}
    ]
  }
}
```

### 3. Start Metadata Service (3 nodes - Raft cluster)

```bash
# Terminal 1 - Metadata Node 1
cd dmq-metadata-service
mvn spring-boot:run -Dspring-boot.run.arguments="--kraft.node-id=1 --server.port=9091"

# Terminal 2 - Metadata Node 2  
cd dmq-metadata-service
mvn spring-boot:run -Dspring-boot.run.arguments="--kraft.node-id=2 --server.port=9092"

# Terminal 3 - Metadata Node 3
cd dmq-metadata-service
mvn spring-boot:run -Dspring-boot.run.arguments="--kraft.node-id=3 --server.port=9093"
```

**Wait for Raft leader election** - Look for logs:
```
🎖️ Node 2 became leader for term 1
```

### 4. Start Storage Service (2+ brokers)

```bash
# Terminal 4 - Storage Broker 101
cd dmq-storage-service
mvn spring-boot:run -Dspring-boot.run.arguments="--dmq.broker.id=101 --server.port=8081"

# Terminal 5 - Storage Broker 102
cd dmq-storage-service
mvn spring-boot:run -Dspring-boot.run.arguments="--dmq.broker.id=102 --server.port=8082"
```

**Look for successful startup logs**:
```
🔍 Discovering controller on startup...
✅ Initial controller discovery successful: http://localhost:9092
📝 Registering broker 101 with controller...
📡 Pulling initial metadata from controller...
✅ Heartbeat ACK for broker 101
```

### 5. Verify Cluster Status

```bash
# Check controller info (query any metadata node)
curl http://localhost:9091/api/v1/metadata/controller

# Expected response:
{
  "controllerId": 2,
  "controllerUrl": "http://localhost:9092",
  "controllerTerm": 1,
  "timestamp": 1731347234567
}

# Check all brokers
curl http://localhost:9092/api/v1/metadata/brokers

# Expected response:
[
  {
    "brokerId": 101,
    "host": "localhost",
    "port": 8081,
    "status": "ONLINE",
    "lastHeartbeat": 1731347235000
  },
  {
    "brokerId": 102,
    "host": "localhost",
    "port": 8082,
    "status": "ONLINE",
    "lastHeartbeat": 1731347235000
  }
]

# Check Raft status
curl http://localhost:9092/api/v1/raft/status

# Create a test topic
curl -X POST http://localhost:9092/api/v1/metadata/topics \
  -H "Content-Type: application/json" \
  -d '{
    "topicName": "test-topic",
    "numPartitions": 3,
    "replicationFactor": 2
  }'

# List all topics
curl http://localhost:9092/api/v1/metadata/topics
```

## 📦 Components

### 1. dmq-common (Shared Library)

**Purpose**: Shared utilities and data structures

**Key Components**:
- **DTOs**: `ControllerInfo`, `BrokerInfo`, `HeartbeatRequest`, `HeartbeatResponse`
- **Configuration**: `ClusterTopologyConfig`, `ServiceDiscovery`
- **Models**: `Topic`, `Partition`, `BrokerNode`

**Maven Dependency**:
```xml
<dependency>
    <groupId>com.distributedmq</groupId>
    <artifactId>dmq-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. dmq-metadata-service (KRaft Controller)

**Purpose**: Cluster coordination and metadata management via Raft consensus

**Ports**: 9091, 9092, 9093  
**Database**: PostgreSQL/H2  
**Technology**: Spring Boot + Custom Raft implementation

**Key Components**:
- **RaftController**: Implements Raft consensus (leader election, log replication)
- **RaftLogPersistence**: Persistent log storage (`raft-data/node-{id}/log.json`)
- **MetadataStateMachine**: Applies committed commands (RegisterBroker, CreateTopic, UpdateBrokerStatus)
- **HeartbeatService**: Tracks broker health, marks OFFLINE after 30s
- **MetadataController**: REST API for cluster operations
- **HeartbeatController**: Receives broker heartbeats (with leader validation)
- **RaftApiController**: Raft protocol endpoints (RequestVote, AppendEntries)
- **MetadataPushService**: Pushes CONTROLLER_CHANGED notifications

**Configuration** (`application.yml`):
```yaml
kraft:
  node-id: 1  # Must be 1, 2, or 3
  raft:
    election-timeout-ms: 5000
    heartbeat-interval-ms: 1500
    log-dir: raft-data

spring:
  datasource:
    url: jdbc:h2:mem:metadata
    # url: jdbc:postgresql://localhost:5432/distributedmq
```

### 3. dmq-storage-service (Storage Broker)

**Purpose**: Message storage and partition replication

**Ports**: 8081, 8082, 8083, 8084, 8085  
**Technology**: Spring Boot

**Key Components**:
- **HeartbeatSender**: 
  - Controller discovery on startup (parallel queries)
  - Periodic heartbeat (5s interval)
  - Automatic controller failover detection
  - Syncs controller info before each heartbeat
- **ControllerDiscoveryService**: Discovers controller via parallel queries to all metadata nodes
- **MetadataStore**: 
  - Local metadata cache
  - Version tracking
  - CONTROLLER_CHANGED notification handling
- **StorageService**: Message storage operations
- **ReplicationManager**: Partition replication logic
- **StorageController**: REST API for message operations

**Configuration** (`application.yml`):
```yaml
dmq:
  broker:
    id: 101  # Unique broker ID
    host: localhost
    port: 8081

storage:
  data-dir: data/broker-101
  heartbeat:
    interval-ms: 5000
    retry-attempts: 3
    failure-threshold: 3  # Trigger rediscovery after 3 failures
```

### 4. dmq-client (Client Library)

**Purpose**: Producer/Consumer client library

**Key Components**:
- **Producer**: Message publishing
- **Consumer**: Message consumption
- **MetadataClient**: Cluster metadata discovery

## 📚 API Documentation

### Metadata Service REST APIs

#### Controller Management
```bash
# Get current controller info (any node)
GET /api/v1/metadata/controller
Response: {
  "controllerId": 2,
  "controllerUrl": "http://localhost:9092",
  "controllerTerm": 3,
  "timestamp": 1731347234567
}

# Get cluster metadata (any node)
GET /api/v1/metadata/cluster
Response: {
  "version": 15,
  "brokers": [...],
  "topics": [...],
  "controllerLeaderId": 2,
  "totalPartitions": 12,
  "controllerInfo": {...},
  "activeMetadataNodes": [...]
}
```

#### Topic Management (Leader Only)
```bash
# Create topic
POST /api/v1/metadata/topics
Content-Type: application/json
{
  "topicName": "orders",
  "numPartitions": 3,
  "replicationFactor": 2
}

Response (Success): 201 Created
Response (Non-Leader): 503 Service Unavailable
  X-Controller-Leader: 2

# Get topic info (any node)
GET /api/v1/metadata/topics/{name}

# List all topics (any node)
GET /api/v1/metadata/topics
```

#### Broker Management
```bash
# Register broker (leader only)
POST /api/v1/metadata/brokers
{
  "brokerId": 101,
  "host": "localhost",
  "port": 8081
}

# Get all brokers (any node)
GET /api/v1/metadata/brokers

# Get specific broker (any node)
GET /api/v1/metadata/brokers/{id}

# Broker heartbeat (leader only)
POST /api/v1/metadata/heartbeat/{brokerId}

Response: 200 OK - Heartbeat accepted
Response: 503 Service Unavailable - Not the leader
  X-Controller-Leader: 2
```

#### Raft Protocol APIs
```bash
# Get Raft status (any node)
GET /api/v1/raft/status
Response: {
  "nodeId": 2,
  "currentTerm": 3,
  "state": "LEADER",
  "isLeader": true,
  "leaderId": 2,
  "commitIndex": 145,
  "lastApplied": 145
}

# RequestVote RPC (internal)
POST /api/v1/raft/request-vote

# AppendEntries RPC (internal)
POST /api/v1/raft/append-entries
```

### Storage Service REST APIs

```bash
# Produce message
POST /api/v1/storage/topics/{topic}/partitions/{partition}
Content-Type: application/json
{
  "key": "order-123",
  "value": "order data...",
  "timestamp": 1731347234567
}

# Consume messages
GET /api/v1/storage/topics/{topic}/partitions/{partition}?offset=0&limit=100

# Get replication status
GET /api/v1/storage/replication/status
```

## ⚙️ Configuration

### Service Discovery (`config/services.json`)

Located at project root, this file is **mandatory** for service discovery:

```json
{
  "services": {
    "metadata-services": [
      {"id": 1, "host": "localhost", "port": 9091, "url": "http://localhost:9091"},
      {"id": 2, "host": "localhost", "port": 9092, "url": "http://localhost:9092"},
      {"id": 3, "host": "localhost", "port": 9093, "url": "http://localhost:9093"}
    ],
    "storage-services": [
      {"id": 101, "host": "localhost", "port": 8081, "url": "http://localhost:8081"},
      {"id": 102, "host": "localhost", "port": 8082, "url": "http://localhost:8082"},
      {"id": 103, "host": "localhost", "port": 8083, "url": "http://localhost:8083"}
    ]
  },
  "controller": {
    "electionTimeoutMs": 5000
  },
  "metadata": {
    "sync": {
      "syncTimeoutMs": 30000
    }
  }
}
```

### Key Configuration Parameters

#### Metadata Service
- `kraft.node-id`: Node ID (1, 2, or 3)
- `kraft.raft.election-timeout-ms`: Election timeout (default: 5000ms)
- `kraft.raft.heartbeat-interval-ms`: Leader heartbeat interval (default: 1500ms)
- `kraft.raft.log-dir`: Raft log directory (default: raft-data)

#### Storage Service
- `dmq.broker.id`: Unique broker ID (101, 102, etc.)
- `dmq.storage.heartbeat.interval-ms`: Heartbeat interval (default: 5000ms)
- `dmq.storage.heartbeat.retry-attempts`: Retry attempts per heartbeat (default: 3)
- `storage.metadata.periodic-refresh-interval-ms`: Metadata refresh interval (default: 120000ms)

## 🧪 Testing

### Manual Testing

#### Test Controller Failover
```bash
# 1. Start 3 metadata nodes + 2 storage brokers
# 2. Identify current leader
curl http://localhost:9091/api/v1/metadata/controller

# 3. Kill the leader process (Ctrl+C)
# 4. Wait for new election (~5-10 seconds)
# 5. Verify new leader elected
curl http://localhost:9091/api/v1/metadata/controller

# 6. Check brokers automatically switched
curl http://localhost:9092/api/v1/metadata/brokers
# All brokers should remain ONLINE

# 7. Check storage broker logs
# Should see: "🔄 Controller switch detected: 2 → 3"
```

#### Test Broker Failure
```bash
# 1. Kill one storage broker
# 2. Wait 30 seconds
# 3. Check broker status
curl http://localhost:9092/api/v1/metadata/brokers
# Killed broker should show OFFLINE

# 4. Restart broker
# 5. Broker automatically re-registers and goes ONLINE
```

### Running Unit Tests
```bash
# Run all tests
mvn test

# Run specific module tests
mvn test -pl dmq-metadata-service
mvn test -pl dmq-storage-service

# Skip tests during build
mvn clean install -DskipTests
```

## 📖 Documentation

### Available Documentation
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Complete system architecture, flows, and design patterns
- **[PROJECT_REPORT.md](PROJECT_REPORT.md)** - Implementation status, features, and testing report
- **dmq-metadata-service/README.md** - Metadata service documentation
- **dmq-metadata-service/ARCHITECTURE.md** - KRaft and Raft consensus details
- **dmq-storage-service/README.md** - Storage service documentation
- **dmq-storage-service/ARCHITECTURE.md** - Broker architecture and replication

### Project Structure
```
kafka-clone/
├── config/
│   └── services.json           # Service discovery configuration
├── dmq-common/                 # Shared library
│   └── src/main/java/com/distributedmq/common/
├── dmq-client/                 # Client library
├── dmq-metadata-service/       # KRaft controller
│   ├── raft-data/             # Raft log storage
│   └── src/main/java/com/distributedmq/metadata/
│       ├── coordination/      # Raft implementation
│       ├── service/           # Business logic
│       ├── controller/        # REST controllers
│       └── entity/            # JPA entities
├── dmq-storage-service/       # Storage broker
│   └── src/main/java/com/distributedmq/storage/
│       ├── heartbeat/         # Controller communication
│       ├── replication/       # Metadata and replication
│       ├── service/           # Storage logic
│       └── controller/        # REST controllers
└── pom.xml                    # Parent POM
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License.

## 🔄 Version History

**v1.0.0-SNAPSHOT** (Current)
- ✅ Complete Raft consensus implementation
- ✅ Automatic controller and broker failover
- ✅ Dynamic controller discovery
- ✅ Heartbeat-based health monitoring
- ✅ Topic creation and partition management
- ✅ Leader validation in heartbeat endpoint
- ✅ Metadata synchronization with versioning
- ✅ Comprehensive logging and error handling

---

**DistributedMQ** - A Pub-sub based distributed messaging system with Raft consensus.