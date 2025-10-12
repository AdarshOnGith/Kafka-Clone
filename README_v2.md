# DistributedMQ - Microservices Architecture

## Modern Cloud-Native Distributed Messaging System

A production-grade, microservices-based distributed messaging system inspired by Apache Kafka, built with modern architectural patterns and cloud-native principles.

---

## 🏗️ Architecture Overview

### Microservices-Based Design

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Layer                             │
│  ┌──────────────────┐              ┌──────────────────┐         │
│  │ Producer Client  │              │ Consumer Client  │         │
│  └────────┬─────────┘              └────────┬─────────┘         │
└───────────┼────────────────────────────────┼───────────────────┘
            │                                │
            └────────────┬───────────────────┘
                         │
            ┌────────────▼────────────┐
            │    API Gateway          │
            │  - Authentication       │
            │  - Rate Limiting        │
            │  - Service Discovery    │
            └────────┬────────┬───────┘
                     │        │
        ┌────────────┘        └────────────┐
        │                                  │
┌───────▼──────────┐            ┌─────────▼─────────┐
│ Producer         │            │ Consumer          │
│ Ingestion        │            │ Egress            │
│ Service          │            │ Service           │
│ - Partitioning   │            │ - Group Mgmt      │
│ - Batching       │            │ - Offset Mgmt     │
└───────┬──────────┘            └─────────┬─────────┘
        │                                  │
        └────────────┬─────────────────────┘
                     │
        ┌────────────▼────────────┐
        │  Metadata Service       │
        │  - Topic Metadata       │
        │  - Partition Leaders    │
        │  - Consumer Offsets     │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │  Storage Service        │
        │  (Multiple Nodes)       │
        │  - Leader/Follower      │
        │  - WAL Storage          │
        │  - Replication          │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │  Controller Service     │
        │  - Failure Detection    │
        │  - Leader Election      │
        │  - Cluster Coordination │
        └─────────────────────────┘
                     │
        ┌────────────▼────────────┐
        │  Coordination Store     │
        │  (etcd/ZooKeeper)       │
        └─────────────────────────┘
```

---

## 🎯 Key Features

### 1. **Microservices Architecture**
- Independent service deployment and scaling
- Clear separation of concerns
- Fault isolation between services
- Technology flexibility per service

### 2. **API Gateway Pattern**
- Single entry point for all clients
- Centralized authentication and authorization
- Rate limiting and throttling
- Service discovery integration

### 3. **High Throughput & Low Latency**
- Asynchronous processing
- Batching and compression
- Zero-copy data transfer
- Efficient serialization (gRPC)

### 4. **Fault Tolerance**
- Automatic leader election
- Replica synchronization
- Graceful degradation
- Self-healing clusters

### 5. **Scalability**
- Horizontal scaling of all services
- Partition-based parallelism
- Independent service scaling
- Cloud-native deployment

---

## 📦 Microservices Breakdown

### 1. API Gateway Service
**Responsibilities:**
- Request routing to appropriate services
- Authentication and authorization
- Rate limiting and throttling
- Request/response transformation
- Service discovery integration

**Technology:** Spring Cloud Gateway / Netflix Zuul

---

### 2. Producer Ingestion Service
**Responsibilities:**
- Receive messages from producers
- Apply partitioning logic (hash-based or custom)
- Batch messages for efficiency
- Route messages to correct storage nodes
- Return acknowledgments

**Key Operations:**
- Partition assignment
- Leader discovery via Metadata Service
- Message validation
- Batching and compression

---

### 3. Consumer Egress Service
**Responsibilities:**
- Handle consumer subscriptions
- Manage consumer groups
- Track partition assignments
- Coordinate consumer rebalancing
- Deliver messages to consumers

**Key Operations:**
- Consumer group coordination
- Partition assignment
- Offset management
- Rebalancing protocol

---

### 4. Metadata Service
**Responsibilities:**
- Store and serve cluster metadata
- Track topic and partition information
- Maintain partition leader information
- Store consumer group offsets
- Provide service discovery

**Data Stored:**
- Topics and partition mappings
- Partition leaders and ISR lists
- Consumer group offsets
- Cluster topology

**Storage:** PostgreSQL / etcd

---

### 5. Storage Service
**Responsibilities:**
- Persist messages to disk (WAL)
- Replicate data across nodes
- Serve read requests
- Maintain In-Sync Replicas (ISR)
- Handle log compaction

**Deployment:** Multiple instances (storage nodes)

**Key Components:**
- Write-Ahead Log (WAL)
- Replication protocol
- Leader/Follower roles
- ISR management

---

### 6. Controller Service
**Responsibilities:**
- Monitor cluster health
- Detect node failures
- Perform leader election
- Coordinate cluster changes
- Maintain cluster state

**Key Operations:**
- Heartbeat monitoring
- Failure detection
- Leader election algorithm
- Metadata updates

---

### 7. Coordination Store
**Responsibilities:**
- Distributed locking
- Leader election for Controller
- Ephemeral node tracking
- Configuration management

**Technology:** etcd or Apache ZooKeeper

---

## 🔄 Core Flows

### Flow 1: Producer Publishes Message (Write Path)

```
Producer Client
    │
    │ POST /produce/{topic}
    ▼
API Gateway
    │
    │ Route to Producer Ingestion Service
    ▼
Producer Ingestion Service
    │
    │ 1. Partition assignment (hash-based)
    │ 2. Group by partition
    │ 3. Query Metadata Service for leaders
    ▼
Metadata Service
    │
    │ Return leader addresses
    ▼
Producer Ingestion Service
    │
    │ POST /storage/partitions/{id}/append
    ▼
Storage Service (Leader)
    │
    │ 1. Append to local WAL
    │ 2. Replicate to followers
    │ 3. Wait for ISR acks
    │ 4. Return success
    ▼
Response chain back to Producer Client
```

---

### Flow 2: Consumer Reads Message (Read Path)

```
Consumer Client
    │
    │ GET /consume/{group}/{topic}
    ▼
API Gateway
    │
    │ Route to Consumer Egress Service
    ▼
Consumer Egress Service
    │
    │ 1. Check consumer group membership
    │ 2. Get partition assignment
    │ 3. Query Metadata Service for offset & leader
    ▼
Metadata Service
    │
    │ Return offset + leader address
    ▼
Consumer Egress Service
    │
    │ GET /storage/partitions/{id}/fetch
    ▼
Storage Service (Leader)
    │
    │ Read from WAL at offset
    ▼
Response chain back to Consumer Client
    │
Consumer processes messages
    │
    │ POST /consume/{group}/offsets/{topic}/{partition}
    ▼
Consumer Egress Service
    │
    │ Update offset in Metadata Service
    ▼
Offset committed
```

---

### Flow 3: Cluster Self-Healing (Failure Recovery)

```
Controller Service
    │
    │ Monitor heartbeats / Watch etcd ephemeral nodes
    ▼
Detect Storage Node Failure
    │
    │ Query Metadata Service for affected partitions
    ▼
For each partition:
    │
    │ 1. Get ISR list
    │ 2. Select new leader from ISR
    │ 3. Update Metadata Service
    ▼
Metadata Service updated
    │
    │ New leader address stored
    ▼
Client services refresh metadata cache
    │
    │ Next requests route to new leader
    ▼
Cluster healed
```

---

## 🛠️ Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **API Gateway** | Spring Cloud Gateway | Routing, auth, rate limiting |
| **Services** | Spring Boot 3.x | Microservices framework |
| **Communication** | gRPC + REST | Inter-service + client communication |
| **Service Discovery** | Consul / Eureka | Dynamic service location |
| **Metadata Store** | PostgreSQL | Structured metadata storage |
| **Coordination** | etcd | Distributed coordination |
| **Storage** | Custom WAL | Message persistence |
| **Serialization** | Protocol Buffers | Efficient binary encoding |
| **Monitoring** | Prometheus + Grafana | Metrics and dashboards |
| **Logging** | ELK Stack | Centralized logging |
| **Container** | Docker | Service containerization |
| **Orchestration** | Kubernetes | Container orchestration |

---

## 📊 Project Structure

```
DistributedMQ/
├── dmq-common/                    # Shared libraries
│   ├── dmq-common-models/        # Data models
│   ├── dmq-common-proto/         # Protocol Buffers definitions
│   └── dmq-common-utils/         # Utilities
│
├── dmq-api-gateway/              # API Gateway Service
│   └── src/main/java/
│       └── com/distributedmq/gateway/
│
├── dmq-producer-ingestion/       # Producer Ingestion Service
│   └── src/main/java/
│       └── com/distributedmq/producer/
│
├── dmq-consumer-egress/          # Consumer Egress Service
│   └── src/main/java/
│       └── com/distributedmq/consumer/
│
├── dmq-metadata-service/         # Metadata Service
│   └── src/main/java/
│       └── com/distributedmq/metadata/
│
├── dmq-storage-service/          # Storage Service
│   └── src/main/java/
│       └── com/distributedmq/storage/
│
├── dmq-controller-service/       # Controller Service
│   └── src/main/java/
│       └── com/distributedmq/controller/
│
├── dmq-client-sdk/               # Client SDK (Producer/Consumer)
│   └── src/main/java/
│       └── com/distributedmq/client/
│
├── docker/                       # Docker configurations
├── kubernetes/                   # K8s manifests
└── docs/                         # Documentation
```

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 14+
- etcd 3.5+

### Quick Start (Local Development)

```bash
# Start infrastructure
docker-compose up -d postgres etcd

# Build all services
mvn clean install

# Start services (separate terminals)
cd dmq-metadata-service && mvn spring-boot:run
cd dmq-storage-service && mvn spring-boot:run
cd dmq-controller-service && mvn spring-boot:run
cd dmq-producer-ingestion && mvn spring-boot:run
cd dmq-consumer-egress && mvn spring-boot:run
cd dmq-api-gateway && mvn spring-boot:run
```

### Using the System

```java
// Producer Example
DMQProducer producer = DMQProducer.builder()
    .gatewayUrl("http://localhost:8080")
    .build();

producer.send("my-topic", "key-1", "message-1");

// Consumer Example
DMQConsumer consumer = DMQConsumer.builder()
    .gatewayUrl("http://localhost:8080")
    .groupId("my-group")
    .build();

consumer.subscribe("my-topic");
List<Record> records = consumer.poll(Duration.ofMillis(100));
```

---

## 📈 Scalability

### Independent Service Scaling

```bash
# Scale Producer Ingestion Service
kubectl scale deployment producer-ingestion --replicas=5

# Scale Storage Service
kubectl scale deployment storage-service --replicas=10

# Scale Consumer Egress Service
kubectl scale deployment consumer-egress --replicas=3
```

---

## 🎓 Learning Outcomes

This project demonstrates:
- ✅ Microservices architecture patterns
- ✅ API Gateway pattern
- ✅ Service discovery and registration
- ✅ Distributed coordination (etcd)
- ✅ Leader election algorithms
- ✅ Fault tolerance and self-healing
- ✅ gRPC for inter-service communication
- ✅ RESTful APIs for client communication
- ✅ Container orchestration (Kubernetes)
- ✅ Cloud-native principles

---

## 📖 Documentation

- [Architecture Deep Dive](docs/ARCHITECTURE.md)
- [API Reference](docs/API_REFERENCE.md)
- [Deployment Guide](docs/DEPLOYMENT.md)
- [Development Guide](docs/DEVELOPMENT.md)

---

## 🏆 Why This Architecture?

1. **Production-Ready**: Used by real companies (Confluent Cloud, AWS MSK)
2. **Scalable**: Scale services independently based on load
3. **Resilient**: Failure of one service doesn't crash entire system
4. **Maintainable**: Clear boundaries, easier to debug and update
5. **Cloud-Native**: Ready for Kubernetes deployment
6. **Educational**: Demonstrates modern distributed systems patterns

---

## 📞 Project Information

**Course**: Distributed Systems  
**Architecture**: Microservices-based  
**Pattern**: Cloud-Native  
**Deployment**: Kubernetes-ready

---

**Version**: 2.0.0 (Microservices Architecture)  
**Last Updated**: October 12, 2025
