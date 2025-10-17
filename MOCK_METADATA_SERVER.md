# Mock Metadata Server Setup

## ✅ What Was Created:

### 1. **cluster-metadata.json**
**Location**: `dmq-client/src/main/resources/cluster-metadata.json`
**Format**: Your friend's cluster format
```json
{
  "brokers": [
    {"id": 1, "host": "localhost", "port": 8081, ...},
    {"id": 2, "host": "localhost", "port": 8082, ...},
    {"id": 3, "host": "localhost", "port": 8083, ...}
  ],
  "partitions": [
    {"topic": "test-topic", "partition": 0, "leaderId": 1, ...},
    {"topic": "orders", "partition": 0, "leaderId": 2, ...},
    {"topic": "orders", "partition": 1, "leaderId": 3, ...}
  ]
}
```

### 2. **MockMetadataServer.java**
**Location**: `dmq-client/src/main/java/com/distributedmq/client/test/MockMetadataServer.java`
**Purpose**: HTTP server that converts your friend's format to producer's expected format

## 🚀 How to Run:

### Start Mock Metadata Server:
```bash
cd dmq-client
mvn compile exec:java
```

Server starts on **http://localhost:8081**

## 📡 Available Endpoints:

### Get Topic Metadata:
```
GET http://localhost:8081/api/v1/metadata/topics/{topicName}
```

### Available Topics:
- `test-topic` (1 partition)
- `orders` (2 partitions)

## 🔄 Format Conversion:

### Your Friend's Format (Input):
```json
{
  "brokers": [...],
  "partitions": [...]
}
```

### Producer's Expected Format (Output):
```json
{
  "topicName": "orders",
  "partitionCount": 2,
  "partitions": [
    {
      "partitionId": 0,
      "leader": {
        "brokerId": 2,
        "host": "localhost",
        "port": 8082,
        "address": "localhost:8082",
        "status": "ONLINE"
      }
    }
  ]
}
```

## 🧪 Test It:

### Using curl:
```bash
curl http://localhost:8081/api/v1/metadata/topics/orders
```

### Using Producer:
```bash
# Update pom.xml mainClass to TestMetadataFetch or EndToEndProducerTest
mvn compile exec:java
```

## 📝 Next Steps:

1. ✅ Mock Metadata Server is running on port 8081
2. ⏳ Your friend needs to start Storage Services on:
   - Port 8081 (broker 1)
   - Port 8082 (broker 2) 
   - Port 8083 (broker 3)
3. 🧪 Then run end-to-end test with real storage services

## 🔧 Adding New Topics:

Edit `cluster-metadata.json` and add to partitions array:
```json
{
  "topic": "new-topic",
  "partition": 0,
  "leaderId": 1,
  "followerIds": [2, 3],
  "isrIds": [1, 2],
  "leaderEpoch": 1
}
```

Restart mock server to load new configuration.
