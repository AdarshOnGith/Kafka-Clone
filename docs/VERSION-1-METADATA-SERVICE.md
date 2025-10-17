# Version 1: Metadata Service Implementation

## Overview
This document describes the initial implementation of the Consumer Client Library and its integration with the Consumer Egress Service (CES). This is the first version focusing on basic subscribe functionality with a single consumer per group.

---

## Story: Building the Consumer Subscribe Flow

### The Beginning: Consumer Configuration
We started by creating a way for users to configure their consumers. The `ConsumerConfig` class was designed to hold all necessary settings:
- **metadataServiceUrl**: Where the Consumer Egress Service (CES) is running
- **groupId**: Consumer group identifier
- **clientId**: Application identifier
- Other settings like auto-commit, offset reset, polling limits

Users create a config and pass it to the consumer. This config contains the crucial URL that tells the consumer library where to send network requests.

### Creating the Consumer
The `DMQConsumer` class is the main implementation that users interact with. When you create a consumer:
1. It extracts the CES URL from the config
2. Creates a unique `consumerId` by combining clientId with a random UUID
3. Initializes the `ConsumerEgressClient` (the network layer) with the CES URL
4. Sets up internal maps to track partition metadata, fetch positions, and committed offsets

Three important maps were created:
- **partitionMetadata**: Stores complete partition information (leader, ISR, offsets)
- **fetchPositions**: Tracks the current offset to fetch next for each partition
- **committedOffsets**: Saves the last committed checkpoint for each partition

### The Network Layer
The `ConsumerEgressClient` class handles all HTTP communication. It:
- Stores the CES URL provided during initialization
- Uses Spring's RestTemplate to make HTTP calls
- Builds full endpoint URLs by combining base URL + endpoint path
- Handles network errors gracefully and returns error responses

### The Subscribe Method
The heart of the consumer is the `subscribe()` method. Here's what happens:

**Step 1: Validation**
- Check if consumer is closed
- Verify topics list is not null or empty
- If validation fails, throw an exception immediately

**Step 2: Build Request**
- Create a `ConsumerSubscriptionRequest` with:
  - groupId (from config)
  - consumerId (unique ID)
  - topics (list of all topics user wants to subscribe to)
  - clientId (from config)

**Step 3: Network Call**
- Send HTTP POST to `{cesUrl}/api/consumer/join-group`
- CES processes the request and returns partition metadata
- If CES returns success=false, throw exception

**Step 4: Process Response**
- Clear existing partition metadata and fetch positions
- Loop through each `PartitionMetadata` in response
- For each partition:
  - Create a `TopicPartition` key (topic + partition ID)
  - Store the complete metadata in partitionMetadata map
  - Initialize fetchPositions to currentOffset (where to start reading)
  - Initialize committedOffsets to the same value
  - Log which broker is leading this partition

**Step 5: Mark Ready**
- Set subscribed flag to true
- Consumer is now ready to poll messages

### Understanding the Data Models

**ConsumerSubscriptionRequest** (what we send):
- groupId: Which consumer group
- consumerId: Unique consumer identifier
- topics: List of topics to subscribe to (ALL topics in one request)
- clientId: Application name

**ConsumerSubscriptionResponse** (what we receive):
- success: Whether request succeeded
- errorMessage: Error details if failed
- groupId: Confirmed group ID
- partitions: List of PartitionMetadata objects
- generationId: For future rebalancing (Phase 2)

**PartitionMetadata** (critical shared model):
This is a global model used across all services. We had to be very careful not to rename or delete existing fields. It contains:
- Original fields: topicName, partitionId, leader, replicas, isr, startOffset, endOffset
- New fields added: currentOffset, highWaterMark, isrBrokerIds
- Convenience methods: getTopic(), getPartition() for backward compatibility

**Important lesson learned**: PartitionMetadata is shared across dmq-client, dmq-metadata-service, and dmq-storage-service. We cannot rename original fields without breaking other services. Instead, we added convenience methods.

### The TopicPartition Helper
A simple class to use as a map key. It combines topic name and partition ID into a single object with proper equals() and hashCode() methods. This lets us use it in HashMap to track data per partition.

### Request/Response Flow
When you call `consumer.subscribe(Arrays.asList("orders", "payments"))`:

1. Consumer sends ONE request with ALL topics
2. CES validates all topics exist
3. If ANY topic is invalid, entire request fails (all-or-nothing)
4. If all valid, CES returns metadata for ALL partitions from ALL topics
5. Consumer stores metadata for each partition
6. Consumer initializes fetch positions to start reading

This is efficient - single round trip for multiple topics.

### Error Handling
We handle three types of errors:

**Client-side validation errors**:
- Empty or null topics list → IllegalArgumentException
- Consumer already closed → IllegalStateException

**Network errors**:
- CES unavailable → RuntimeException with network error message
- Timeout → RestClientException caught and wrapped

**CES business errors**:
- Topics don't exist → success=false with error message
- Invalid group ID → success=false with error message

### Testing Infrastructure
To test the subscribe functionality without a real CES backend, we created:

**Mock CES Server** (`mock-ces-server.py`):
- Flask-based HTTP server
- Simulates CES endpoints
- Returns dummy partition metadata
- Has hardcoded topics: orders (2 partitions), payments (1 partition), inventory (2 partitions)
- Validates incoming requests and returns appropriate responses

**Enhanced Test Suite** (`test-subscribe.py`):
- Organized tests by category: BASIC, EDGE_CASE, ERROR, VALIDATION, PERFORMANCE, IDEMPOTENCY
- 20+ comprehensive test cases covering:
  - Basic functionality: single topic, multiple topics, all topics
  - Edge cases: empty list, duplicate topics, long IDs, special characters, same consumer twice
  - Error handling: non-existent topics, mixed valid/invalid, all invalid
  - Validation: missing fields, null values
  - Performance: many topics, large payloads, response time checks
  - Idempotency: multiple consumers in same group
- Enhanced validation: checks response structure, partition count, topic names, no duplicates
- Performance monitoring: tracks response times
- Detailed reporting: results by category, pass rates, failed test details

### Key Design Decisions

**All-or-Nothing Subscription**:
Currently, if you subscribe to ["orders", "payments", "fake-topic"], the entire request fails. We don't do partial success. This is simpler for Phase 1. Future versions might allow subscribing to valid topics with warnings.

**Single Consumer Per Group**:
Phase 1 keeps it simple - one consumer per group. No rebalancing, no coordination. Future phases will add multi-member groups.

**Centralized Metadata**:
All partition information comes from CES. Consumer doesn't directly talk to metadata service. This separation allows CES to handle group coordination logic.

**Immutable Shared Models**:
PartitionMetadata is used everywhere. We learned to never rename/delete existing fields. Only ADD new fields and provide compatibility methods.

**URL Configuration**:
User provides CES URL in config. This flows through: ConsumerConfig → DMQConsumer → ConsumerEgressClient. Allows different URLs for dev/docker/production.

### What's Working Now
- ✅ Consumer creation with configuration
- ✅ Subscribe to single or multiple topics
- ✅ Network communication with CES
- ✅ Partition metadata storage
- ✅ Fetch position initialization
- ✅ Error handling and validation
- ✅ Comprehensive test coverage
- ✅ Mock server for testing

### What's Not Implemented Yet
- ❌ poll() method (fetching actual messages)
- ❌ commitSync/commitAsync (saving offsets)
- ❌ assign() method (manual partition assignment)
- ❌ seek() methods (manual offset control)
- ❌ Multi-member consumer groups
- ❌ Rebalancing
- ❌ Heartbeat mechanism
- ❌ Auto-commit functionality

### Files Modified/Created

**Core Implementation**:
- `dmq-client/src/main/java/com/distributedmq/client/consumer/DMQConsumer.java` - Main consumer implementation with subscribe()
- `dmq-client/src/main/java/com/distributedmq/client/consumer/ConsumerConfig.java` - Configuration class
- `dmq-client/src/main/java/com/distributedmq/client/consumer/ConsumerEgressClient.java` - Network layer
- `dmq-client/src/main/java/com/distributedmq/client/consumer/TopicPartition.java` - Helper class for map keys
- `dmq-client/src/main/java/com/distributedmq/client/consumer/Consumer.java` - Interface

**Data Transfer Objects**:
- `dmq-common/src/main/java/com/distributedmq/common/dto/ConsumerSubscriptionRequest.java` - Subscribe request
- `dmq-common/src/main/java/com/distributedmq/common/dto/ConsumerSubscriptionResponse.java` - Subscribe response

**Shared Models**:
- `dmq-common/src/main/java/com/distributedmq/common/model/PartitionMetadata.java` - Fixed to preserve original fields, added convenience methods

**Testing**:
- `test/mock-ces-server.py` - Flask-based mock CES server
- `test/test-subscribe.py` - Enhanced test suite with 20+ test cases

**Documentation**:
- `docs/CONSUMER_SUBSCRIPTION_FLOW.md` - Request/response flow explanation
- `docs/PARTITION_METADATA_COMPATIBILITY.md` - Type compatibility explanation
- `docs/CONSUMER_CALL_FLOW.md` - Complete call flow from user to CES

---

## Current State: Phase 1 Complete ✅

The consumer can now successfully subscribe to topics and receive partition metadata. The foundation is solid and ready for the next phase: implementing poll() to fetch actual messages from storage nodes.

**Key Achievement**: Built a working subscribe flow with proper separation of concerns (config → consumer → network layer → CES), comprehensive error handling, and extensive test coverage. The architecture is clean and extensible for future features.

