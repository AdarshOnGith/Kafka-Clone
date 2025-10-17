# Phase 1 Implementation - Metadata Fetch

## ✅ What We Implemented

### 1. **RequestFormatter** (dmq-common)
- `RequestFormatter.java` - Centralized request/response formatting
- Methods:
  - `buildMetadataRequestUrl()` - Build metadata service URLs
  - `buildProduceRequestUrl()` - Build storage service URLs
  - `parseMetadataResponse()` - Parse JSON to TopicMetadata
  - `parseProduceResponse()` - Parse JSON to ProduceResponse

### 2. **Producer Configuration**
- `producer-config.properties` - External configuration file
- `ProducerConfig.java` - Updated with:
  - `loadFromFile()` - Load from filesystem
  - `loadFromResource()` - Load from classpath

### 3. **MetadataServiceClient** (dmq-client)
- `MetadataServiceClient.java` - HTTP client for Metadata Service
- Uses Java 11+ HttpClient
- Uses RequestFormatter for URL building and response parsing

### 4. **DMQProducer** (Updated)
- Simplified to use MetadataServiceClient
- Phase 1: Only fetches metadata
- Added detailed logging

### 5. **Test Application**
- `TestMetadataFetch.java` - Test program
- Loads config → Creates producer → Fetches metadata

### 6. **Metadata Service Stub**
- `MetadataController.java` - Updated getTopicMetadata()
- Returns mock data:
  - 3 partitions
  - 3 brokers (localhost:8082, 8083, 8084)
  - Replication factor 3

---

## 🧪 How to Test

### Step 1: Compile Projects

```powershell
# From project root
cd c:\Users\viral\OneDrive\Desktop\Kafka-Clone

# Build all modules
mvn clean install
```

### Step 2: Start Metadata Service

```powershell
# Terminal 1
cd dmq-metadata-service
mvn spring-boot:run
```

**Wait for:** `Started MetadataServiceApplication on port 8081`

### Step 3: Run Test Client

```powershell
# Terminal 2
cd dmq-client
mvn exec:java -Dexec.mainClass="com.distributedmq.client.test.TestMetadataFetch"
```

---

## ✅ Expected Output

### Terminal 1 (Metadata Service):
```
[INFO] Started MetadataServiceApplication on port 8081
...
📥 Received request for topic metadata: test-topic
⚠️  Returning mock metadata (Phase 1 stub)
✅ Returning metadata for topic 'test-topic': 3 partitions
```

### Terminal 2 (Producer Test):
```
╔════════════════════════════════════════╗
║  DMQ Producer - Metadata Fetch Test   ║
╚════════════════════════════════════════╝

[INFO] Loading producer configuration...
✅ Configuration loaded
   Metadata Service URL: http://localhost:8081

[INFO] Creating DMQ Producer...
✅ Producer created

[INFO] Testing metadata fetch for topic 'test-topic'...

========================================
PRODUCER: Sending message to topic 'test-topic'
Key: user-123, Value size: 26 bytes
STEP 1: Fetching metadata for topic 'test-topic'...
✅ Successfully fetched metadata:
  - Topic: test-topic
  - Partition Count: 3
  - Partitions: 3
========================================

═══════════════════════════════════════
         TEST COMPLETED
═══════════════════════════════════════
✅ Metadata fetch successful!
✅ Producer closed
```

---

## 📁 Files Created/Modified

### Created:
- `dmq-common/src/main/java/com/distributedmq/common/util/RequestFormatter.java`
- `dmq-client/src/main/resources/producer-config.properties`
- `dmq-client/src/main/java/com/distributedmq/client/producer/client/MetadataServiceClient.java`
- `dmq-client/src/main/java/com/distributedmq/client/test/TestMetadataFetch.java`

### Modified:
- `dmq-client/src/main/java/com/distributedmq/client/producer/ProducerConfig.java`
- `dmq-client/src/main/java/com/distributedmq/client/producer/DMQProducer.java`
- `dmq-metadata-service/.../controller/MetadataController.java`

---

## 🎯 Phase 1 Complete!

**What Works:**
✅ Producer reads config from file
✅ Producer creates MetadataServiceClient
✅ Producer calls Metadata Service via HTTP
✅ Metadata Service returns mock topic metadata
✅ Producer receives and parses metadata

**Next Phase:**
- Determine partition (hash-based)
- Send messages to Storage Service
- Implement StorageServiceClient
