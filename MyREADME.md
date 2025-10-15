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
### 2. **High Throughput & Low Latency**
### 3. **Fault Tolerance**s
### 4. **Scalability**

---
### Admin client for cluster management
### 1. Producer Client side responsibilities
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

### 2. Consumer Client side responsibilities (to cross check)
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

## 📦 Microservice Breakdown

## 🧠 Functional Breakdown

### 1. 📇 Metadata Service

#### a. **Metadata Subsystem**
**Responsibilities:**
- Track topic, partition, and leader information
- Store and serve metadata to producers/consumers
- Maintain consumer group offsets
- Provide discovery for storage nodes

**Data Stored:**
- Topics and partitions
- Partition leaders and ISR list
- Consumer group offsets
- Cluster topology

**Storage:** PostgreSQL or similar relational DB

---

#### b1. **Controller Subsystem**
**Responsibilities:**
- Detect broker/storage node failures
- Perform partition leader elections
- Coordinate replication and ISR tracking
- Update metadata based on cluster state changes

**Components:**
- Write-Ahead Log (WAL) for changes
- Leader/follower coordination
- Heartbeat monitoring
- Leader election logic
- Metadata broadcasting to all nodes
---

#### b2. **Cluster Coordination Subsystem**
**Responsibilities:**
- Distributed locking
- Leader election for controller role
- Ephemeral node tracking
- Configuration synchronization across nodes
---

### 2. 🗄️ Storage Service

**Responsibilities:**
- Persist messages using Write-Ahead Log (WAL)
- Handle partition leadership (leader/follower role duties)
- Replicate data to ISR nodes
- Serve read requests to consumers
- Manage log retention and compaction
---
---
Note: Each microservice has a logic at entry point, where it performs sanity checks and req validations before processing requests.
- Use Java/spring and maven
- Use a layered architechture for each module.

---



## 🔄 Core System Flows

---

### 🔁 Flow 1: Producer Publishes Message (Write Path)

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

#### 🔍 Additional Notes:
- Producer initially uses **bootstrap metadata nodes** to fetch metadata.
- On metadata fetch:
  - Metadata service validates the request.
  - If the topic doesn't exist, it routes to controller to create it.
- Uses metadata to get partition leader and target broker (storage node).
- Storage node validates and processes the produce request.

---

### 🧱 Kafka-Inspired Internal Broker Logic (Simplified)

#### Kafka-Inspired Steps:

1. **Receive & Parse Request**
   - Authn/Authz
   - Parse topic, partition, records, acks, producer ID/epoch, txn info

2. **Validation & Quotas**
   - Check topic/partition existence
   - Authorization & quotas
   - Idempotency checks

3. **Append to WAL**
   - Assign offsets
   - Write to local log segment

4. **Replication to ISR**
   - Followers fetch data from leader
   - Leader tracks high watermark (HW)

5. **Acknowledge Based on `acks`:**

| Acks Setting | Behavior                             |
|--------------|--------------------------------------|
| `acks=0`     | Return immediately                   |
| `acks=1`     | Return after write to leader         |
| `acks=all`   | Return after all ISRs replicate      |

6. **Update HW & LEO**
   - HW = last offset replicated to all ISRs
   - LEO = next offset to be written

7. **Send Response to Producer**
   - Includes topic, partition, base offset, errors if any

8. **Consumer Visibility**
   - Only messages up to HW are fetchable

---

## ⚙️ When Is Metadata Updated?

Metadata is updated:
- When topics/partitions are created or deleted
- During leader election
- When ISR list changes
    Leader sends updated ISR list to the controller.
    Controller updates cluster metadata (ISR, leader info, etc.).
    Updated metadata is propagated to all metadata brokers.
    Metadata brokers update caches and respond with the latest cluster state to producers and consumers.
- On configuration changes

> ✅ **HW/LEO are local states**, not propagated as cluster metadata  
> 🚫 **Metadata is not updated during normal produce flow**

---

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

## 🔄 Flow 3: Cluster Self-Healing (Failure Recovery)

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

---

### 🧠 Controller Election & Recovery Details

- All controller nodes participate in a **Raft quorum**.
- The **current controller is the Raft leader**.
- If controller fails:
  - Raft detects failure via missed heartbeats
  - Remaining nodes perform **automatic leader election**
  - New Raft leader becomes the **active controller**

**Responsibilities of New Controller:**
- Resume partition leader election
- ISR management
- Metadata propagation
- Cluster-wide coordination
- basically take place of old controller

---

## 📊 Project Structure

```
DistributedMQ/
├── dmq-common/                    # Shared libraries
│           # Data models, # Protocol Buffers definitions, # Utilities etc.
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
|    └── dmq-controller-handler/       # Controller and coordination part
|
├── dmq-storage-service/          # Storage Service
│   └── src/main/java/
│       └── com/distributedmq/storage/
|
├── Admin_Client/  # Very last part to do
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