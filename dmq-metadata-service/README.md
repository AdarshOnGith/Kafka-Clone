# DMQ Metadata Service

## Overview

The Metadata Service is the central coordination point for the Distributed Message Queue system. It manages:

- **Topic & Partition Metadata**: Stores topic configurations, partition assignments, and leader information
- **Consumer Offsets**: Tracks committed offsets for consumer groups
- **Storage Node Registry**: Monitors health and status of storage nodes
- **Leader Election Support**: Provides metadata updates during partition leader changes

## Architecture

### Technology Stack
- **Spring Boot 3.1.5**: Core framework
- **PostgreSQL**: Durable metadata storage
- **Caffeine Cache**: High-performance in-memory caching (60s TTL)
- **gRPC**: Inter-service communication
- **REST API**: Client-facing endpoints
- **Consul**: Service discovery and registration
- **Flyway**: Database migration management

### Key Components

1. **PartitionMetadataService**: Handles partition and leader metadata
2. **ConsumerOffsetService**: Manages consumer offset commits and queries
3. **StorageNodeService**: Monitors storage node health via heartbeats
4. **REST Controllers**: HTTP endpoints for external access
5. **gRPC Server**: High-performance RPC for inter-service calls

## Database Schema

### Core Tables
- `topics`: Topic configuration (partition count, replication factor, retention)
- `partitions`: Partition assignments and leader information
- `partition_replicas`: Replica status and ISR (In-Sync Replicas) tracking
- `consumer_groups`: Consumer group coordination state
- `consumer_offsets`: Committed offsets with leader epoch
- `storage_nodes`: Registered storage nodes with health status

### Views
- `v_partition_details`: Complete partition metadata with ISR counts
- `v_consumer_group_summary`: Consumer group overview with member counts

## API Reference

### REST Endpoints

#### Partition Metadata
```http
GET /api/metadata/partitions/{topic}/{partition}
GET /api/metadata/partitions/{topic}
PUT /api/metadata/partitions/{topic}/{partition}/leader
```

#### Consumer Offsets
```http
GET /api/metadata/offsets/{groupId}/{topic}/{partition}
GET /api/metadata/offsets/{groupId}/{topic}
POST /api/metadata/offsets
POST /api/metadata/offsets/batch
```

#### Storage Nodes
```http
POST /api/metadata/nodes
POST /api/metadata/nodes/{nodeId}/heartbeat
GET /api/metadata/nodes/healthy
DELETE /api/metadata/nodes/{nodeId}
```

### gRPC Methods

```protobuf
service MetadataService {
  rpc GetPartitionMetadata(PartitionMetadataRequest) returns (PartitionMetadataResponse);
  rpc GetTopicMetadata(TopicMetadataRequest) returns (TopicMetadataResponse);
  rpc UpdatePartitionLeader(UpdateLeaderRequest) returns (UpdateLeaderResponse);
  rpc GetConsumerOffset(GetOffsetRequest) returns (GetOffsetResponse);
  rpc CommitConsumerOffset(CommitOffsetRequest) returns (CommitOffsetResponse);
  rpc RegisterStorageNode(RegisterNodeRequest) returns (RegisterNodeResponse);
  rpc NodeHeartbeat(HeartbeatRequest) returns (HeartbeatResponse);
}
```

## Caching Strategy

**Caffeine Cache Configuration**:
- **partitionMetadata**: 60s TTL, 10,000 max entries
- **topicMetadata**: 60s TTL (entire topic metadata)
- **consumerOffsets**: 30s TTL (frequently changing data)

**Cache Eviction**:
- Automatic eviction on leader updates
- Manual eviction via `@CacheEvict` annotations

## Health Monitoring

### Heartbeat Mechanism
Storage nodes send heartbeats every 3 seconds to:
```
POST /api/metadata/nodes/{nodeId}/heartbeat
```

**Failure Detection**:
- **SUSPECT**: No heartbeat for 15 seconds
- **DEAD**: No heartbeat for 60 seconds
- **Automated Detection**: Background task runs every 10 seconds

### Scheduled Tasks
1. **Failure Detection**: Every 10 seconds
2. **Offset Cleanup**: Daily at midnight (removes expired offsets)

## Configuration

### application.yml
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dmq_metadata
    username: dmq_user
    password: dmq_password
  
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema
  
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=60s

server:
  port: 8081

grpc:
  server:
    port: 9091

dmq:
  metadata:
    heartbeat:
      suspect-threshold-seconds: 15
      dead-threshold-seconds: 60
```

## Running the Service

### Prerequisites
1. **PostgreSQL 14+** running on `localhost:5432`
2. **Consul** running on `localhost:8500`

### Database Setup
```sql
CREATE DATABASE dmq_metadata;
CREATE USER dmq_user WITH PASSWORD 'dmq_password';
GRANT ALL PRIVILEGES ON DATABASE dmq_metadata TO dmq_user;
```

### Build and Run
```bash
# Build
mvn clean package

# Run
java -jar target/dmq-metadata-service-1.0.0.jar

# Or with Maven
mvn spring-boot:run
```

### Docker Compose
```bash
docker-compose up -d
```

## Health Checks

**Actuator Endpoints**:
- Health: http://localhost:8081/actuator/health
- Metrics: http://localhost:8081/actuator/metrics
- Prometheus: http://localhost:8081/actuator/prometheus

**Service Discovery**:
- Consul UI: http://localhost:8500/ui
- Service will auto-register as `dmq-metadata-service`

## Integration with Other Services

### Producer Ingestion Service
**Flow 1 Step 4**: Queries partition metadata to find leader
```java
PartitionMetadata metadata = metadataClient.getPartitionMetadata("orders", 0);
String leaderAddress = metadata.getLeaderNodeAddress();
```

### Consumer Egress Service
**Flow 2 Step 3**: Retrieves consumer offsets
```java
ConsumerOffset offset = metadataClient.getConsumerOffset("my-group", "orders", 0);
long startOffset = offset.getOffset();
```

### Controller Service
**Flow 3 Step 4**: Updates partition leader after failure
```java
metadataClient.updatePartitionLeader("orders", 0, "storage-node-02", "node02:9092", 2);
```

### Storage Service
**Heartbeat**: Sends periodic heartbeats
```java
metadataClient.sendHeartbeat("storage-node-01");
```

## Performance Considerations

1. **Read-Heavy Workload**: Metadata reads are 100x more frequent than writes
2. **Caching**: 60s TTL reduces database load by ~95%
3. **Connection Pooling**: HikariCP with 20 max connections
4. **Batch Operations**: Support batch offset commits to reduce transactions
5. **Indexed Queries**: All FK and frequently-queried columns are indexed

## Monitoring & Observability

### Metrics
- Cache hit/miss rates (via Caffeine stats)
- Database query performance (via Hibernate stats)
- gRPC call latencies
- REST endpoint response times

### Logging
- **DEBUG**: Metadata queries and cache operations
- **INFO**: Leader updates, node registrations
- **WARN**: Node status changes (SUSPECT, DEAD)
- **ERROR**: Database errors, gRPC failures

## Failure Scenarios

### PostgreSQL Down
- Service returns 503 Service Unavailable
- Cache continues serving stale data for up to 60s
- Auto-retry with exponential backoff

### Network Partition
- Storage nodes marked SUSPECT then DEAD
- Controller triggers leader re-election
- Metadata updates propagate when connectivity restored

### Cache Inconsistency
- Cache eviction on writes ensures consistency
- Worst case: 60s stale data before auto-expiration

## Future Enhancements

1. **Multi-Region Support**: Geo-distributed metadata replication
2. **etcd Integration**: Distributed coordination for leader election
3. **Change Data Capture**: Stream metadata changes to other services
4. **Advanced Caching**: Redis cluster for distributed cache
5. **GraphQL API**: Flexible querying for dashboards

## Troubleshooting

### Common Issues

**Problem**: Service won't start - "Connection refused to PostgreSQL"
```bash
# Check PostgreSQL is running
pg_isready -h localhost -p 5432

# Verify credentials
psql -h localhost -U dmq_user -d dmq_metadata
```

**Problem**: Nodes stuck in SUSPECT state
```bash
# Check heartbeat interval configuration
# Ensure network connectivity between services
# Verify system clock synchronization (NTP)
```

**Problem**: Cache not updating after leader change
```bash
# Cache eviction should be automatic via @CacheEvict
# Manually clear cache via Spring Boot Actuator:
curl -X DELETE http://localhost:8081/actuator/caches/partitionMetadata
```

## License
Proprietary - Course Project
