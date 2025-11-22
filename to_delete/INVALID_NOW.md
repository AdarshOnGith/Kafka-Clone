# DistributedMQ - Microservices Architecture

## Modern Cloud-Native Distributed Messaging System

A production-grade-like, microservices-based distributed messaging system inspired by Apache Kafka, built with modern architectural patterns and cloud-native principles.

---

## 🏗️ Architecture Overview

### Microservices-Based Design

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client                                  │
│  ┌──────────────────┐              ┌──────────────────┐         │
│  │ Producer Client  │              │ Consumer Client  │         │
│  └────────┬─────────┘              └────────┬─────────┘         │
└───────────┼────────────────────────────────┼───────────────────┘
            │                                │
            └────────────────────────────────┘
                     │        │
                ┌────┘        └──────────────────────────────┐
                │                                            │    
                |                                            |
                |                               ┌────────────▼────────────┐
                |                               │  Storage Service        │
                |                               │  (Multiple Nodes)       |
                │                               |[API-GateWay like logic] |
                |                               │  - Leader/Follower      │
                |                               │  - WAL Storage          │
                |                               │  - Replication          │
                |                               └─────────────────────────┘
                |
┌───────────────▼─────────────────┐
|        Metadata Service         |
|        (Multiple Nodes)         |
|      [API-GateWay like logic]   | 
|    ┌─────────────────────────┐  |  
|    │  Metadata part          │  |
|    │  - Topic Metadata       │  |
|    │  - Partition Leaders    │  |
|    │  - Consumer Offsets     │  |
|    └─────────────────────────┘  |
|    ┌─────────────────────────┐  |
|    │  Controller part        │  |
|    │  - Failure Detection    │  |
|    │  - Leader Election      │  |
|    │  - Cluster Coordination │  |
|    └─────────────────────────┘  |
└─────────────────────────────────┘
                
```

---

## 🎯 Key Features

### 1. **Microservices Architecture**
- Independent service deployment and scaling
- Clear separation of concerns
- Fault isolation between services
- Technology flexibility per service

### 2. **API Gateway Pattern**
- Each node has entry point for a clients as contacted but checks via API-gateway like logic at entry
- Decentralized authentication and authorization
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

### 1. API Gateway like logic
**Responsibilities:**
- Request routing to appropriate services
- Authentication and authorization
- Rate limiting and throttling
- Request/response transformation
- Service discovery integration

**Technology:** Spring Cloud Gateway or similar

---

### 2. Producer Client side responsibilities
**Responsibilities:**
- Receive messages from producers(initiator-service/client)
- Reads/req metadata from metadata service
- Apply partitioning logic (hash-based or custom)
- Batch messages for efficiency
- Route messages to correct storage nodes
- gets acknowledgments

**Key Operations:**
- Partition assignment
- Leader discovery via Metadata Service
- Message validation
- Batching and compression

---

### 3. Consumer Client side responsibilities (to cross check)
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

### 4. Metadata Service [Two parts metadata and controller]
#### a. Metadata
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

**Storage:** PostgreSQL or similar

#### b1. Controller
**Key Components:**
- Write-Ahead Log (WAL)
- Replication protocol
- Leader/Follower roles
- ISR management

**Responsibilities:**
- Monitor cluster health
- Detect node failures
- Perform leader election (parition)
- Coordinate cluster changes
- Maintain cluster state

**Key Operations:**
- Heartbeat monitoring
- Failure detection
- Leader election algorithm
- Metadata updates

---
#### b2. Coordination part
**Responsibilities:**
- Distributed locking
- Leader election for Controller
- Ephemeral node tracking
- Configuration management

---

### 5. Storage Service
**Responsibilities:**
- Persist messages to disk (WAL)
- Replicate data across nodes
- Serve read requests
- Maintain In-Sync Replicas (ISR)
- Handle log compaction

---

## 🔄 Core Flows

### Flow 1: Producer Publishes Message (Write Path)

```
Producer Client
    │
    │ 
    ▼
Producer Ingestion Service
    │
    │ 1. Partition assignment (hash-based)
    │ 2. Group by partition
    │ 3. Query Metadata Service for leaders
    |
    |
    | n/w call
    ▼
API-gateway-like layer of metadata service
Metadata Service
    │
    │ returns metadata requested.
    ▼
Producer Ingestion Service
    │
    │ n/w call to storage node(partition leader)
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


### Addn Details:
kaka produce request client to storage node:
-client uses client side producer service flow to fetch metadata from metadata service using list of bootstrap metadata services (first call it uses bootstrap servers)
- metadata service first validates request then caters to req and returns requested metadata
- checks if topic exist otherwise first calls the controlled to create new topic and update cluster state.
-uses metadata to get information of leader partition and broker(storage node) for the topic
-makes reqw to leader partition broker(storage node) to publish message
- storage validates the req then caters to it

- Kafka Produce Request Flow (Inside the Broker)

Assumptions:

Producer has already fetched metadata

Producer knows the leader broker for the target partition

Step 1: Request Reception & Parsing

Broker receives produce request on its socket

Performs:

Authentication and authorization

Parses:

Topic name

Partition ID

Message batch (records)

Acknowledgment settings (acks)

Producer ID & epoch (for idempotence)

Transaction info (if applicable)

Step 2: Validation and Quota Checks

Broker validates:

Topic and partition exist

Producer is authorized

Producer ID is valid (if idempotent)

Quotas (rate limits, etc.)

📌 Failure in any check results in immediate error response

Step 3: Append Messages to Leader's Local Log

Messages written to leader's active log segment (on disk)

Offsets assigned atomically

Sequence numbers checked for idempotent producers

Step 4: Replication to Followers

Leader asynchronously replicates data to follower brokers

Followers use replica fetcher threads to pull data

Leader tracks each follower's replication progress (High Watermark - HW)

Step 5: Acknowledgment Semantics
acks Setting	Behavior
acks=0	Responds immediately (no durability guarantee)
acks=1	Responds after local log write (leader only)
acks=all / -1	Responds after all in-sync replicas (ISRs) have persisted the message
Step 6: Update High Watermark & Log End Offset

HW (High Watermark): Last offset confirmed by all ISRs

LEO (Log End Offset): Offset of the next message to be written

Step 7: Send Acknowledgment to Producer

Once acks condition is met, broker replies with:

Topic & partition info

Base offset of batch

Any errors (if any)

Step 8: Consumer Visibility

Consumers can only fetch messages up to HW

Prevents reading uncommitted data

📡 When is Kafka Metadata Updated?

Metadata updates occur when:

Topics or partitions are created/deleted

Leader election occurs

ISR list changes (replicas go offline or return)

Configuration changes (topics/brokers)

🚫 Metadata is not updated during normal produce flow.
✅ HW and LEO are local states updated on the broker.

---

### Flow 2: Consumer Reads Message (Read Path)

```
Consumer Client
    │
    │ 
    ▼
Consumer Egress Service
    │
    │ 1. Check consumer group membership
    │ 2. Get partition assignment
    │ 3. Query Metadata Service for offset & leader
    |
    |  n/w call to metadata service
    |
    ▼
Metadata Service
    │
    │ Return requested metadata
    ▼
Consumer Egress Service
    │
    │ n/w call to storage (leader)
    ▼
Storage Service (Leader)
    │
    │ Read from WAL at offset
    ▼
Response chain back to Consumer Client
    │
Consumer processes messages
    │
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
    │ Monitor heartbeats / Watch nodes
    ▼
Detect Storage Node Failure
    │
    │ Query Metadata part for affected partitions
    ▼
For each partition:
    │
    │ 1. Get ISR list
    │ 2. Select new leader from ISR
    │ 3. Update Metadata Service
    ▼
Metadata Service(s) updated and sync-ed
    │
    │ New leader address stored
    ▼
Client services refresh metadata cache
    │
    │ Next requests route to new leader
    ▼
Cluster healed
```


### Details:
The remaining quorum of brokers collectively perform an automatic election to choose a new controller.

All brokers participate in a Raft consensus group (called the metadata quorum).

One broker acts as the leader of this Raft quorum, and that broker is also the active controller.

Failure detection:

If the controller broker (Raft leader) goes down, the other brokers in the quorum detect the failure through missed heartbeats and lack of Raft activity.

New controller election:

Raft protocol automatically elects a new leader from the remaining in-sync brokers in the quorum.

The new Raft leader becomes the new controller.

Cluster metadata updates:

The new controller updates the metadata and resumes control over tasks like:

Leader election for partitions

Replica management

Topic changes
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
│----dmq-client/   
|    ├── dmq-producer-client/        # Producer Ingestion Service
|    │   └── src/main/java/
|    │       └── com/distributedmq/producer/
|    │
|    ├── dmq-consumer-client/          # Consumer Egress Service
|        └── src/main/java/
|            └── com/distributedmq/consumer/
│
├── dmq-metadata-service/         # Metadata Service
|    ├── dmq-metadata-handler/         # Metadata part
|    │   └── src/main/java/
|    │       └── com/distributedmq/metadata/
|    │
|    ├── dmq-controller-handler/       # Controller part
|    │   └── src/main/java/
|    │       └── com/distributedmq/controller/
|
├── dmq-storage-service/          # Storage Service
│   └── src/main/java/
│       └── com/distributedmq/storage/
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

### Quick Start (Local Development)

```bash
# Start infrastructure
docker-compose up -d postgres etcd

# Build all services
mvn clean install


```


## 🏆 Why This Architecture?

1. **Production-Ready**: Used by real companies (Confluent Cloud, AWS MSK)
2. **Scalable**: Scale services independently based on load
3. **Resilient**: Failure of one service doesn't crash entire system
4. **Maintainable**: Clear boundaries, easier to debug and update
5. **Cloud-Native**: Ready for Kubernetes deployment
6. **Educational**: Demonstrates modern distributed systems patterns
---

**Version**: 2.0.0 (Microservices Architecture)  
**Last Updated**: October 12, 2025
