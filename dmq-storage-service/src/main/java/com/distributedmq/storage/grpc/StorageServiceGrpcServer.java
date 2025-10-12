package com.distributedmq.storage.grpc;

import com.distributedmq.common.exception.ErrorCode;
import com.distributedmq.common.exception.StorageException;
import com.distributedmq.common.proto.*;
import com.distributedmq.storage.model.LogRecord;
import com.distributedmq.storage.replication.ReplicationManager;
import com.distributedmq.storage.wal.PartitionLog;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * gRPC server implementation for Storage Service.
 * 
 * Provides:
 * - AppendRecords: Write messages to WAL (Flow 1 Step 5)
 * - FetchRecords: Read messages for consumers (Flow 2 Step 4)
 * - ReplicateRecords: Follower replication endpoint
 * - GetPartitionStatus: Health and offset information
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class StorageServiceGrpcServer extends StorageServiceGrpc.StorageServiceImplBase {
    
    private final PartitionLog partitionLog;
    private final ReplicationManager replicationManager;
    
    @Value("${dmq.storage.node-id}")
    private String nodeId;
    
    /**
     * Append records to the partition log.
     * Called by Producer Ingestion Service (Flow 1 Step 5).
     */
    @Override
    public void appendRecords(AppendRequest request, 
                             StreamObserver<AppendResponse> responseObserver) {
        try {
            log.debug("AppendRecords: topic={}, partition={}, records={}, acks={}", 
                    request.getTopic(), request.getPartition(), 
                    request.getRecordsCount(), request.getRequiredAcks());
            
            String topic = request.getTopic();
            int partition = request.getPartition();
            
            // Convert proto records to internal format
            List<LogRecord> records = new ArrayList<>();
            for (com.distributedmq.common.proto.Record protoRecord : request.getRecordsList()) {
                LogRecord record = LogRecord.builder()
                        .key(protoRecord.getKey())
                        .value(protoRecord.getValue().toByteArray())
                        .timestamp(protoRecord.getTimestamp())
                        .headers(new HashMap<>(protoRecord.getHeadersMap()))
                        .build();
                records.add(record);
            }
            
            // Append to local WAL
            long baseOffset = partitionLog.append(topic, partition, records, 0);
            
            // Replicate to followers if this is the leader
            // TODO: Get replica addresses from Metadata Service
            List<String> replicaAddresses = List.of(); // For now, empty
            
            replicationManager.replicateToFollowers(
                    topic, 
                    partition, 
                    records,
                    replicaAddresses,
                    request.getRequiredAcks()
            ).join(); // Wait for replication based on acks
            
            // Update HWM after replication
            replicationManager.updateHighWaterMark(topic, partition);
            
            // Build response
            AppendResponse response = AppendResponse.newBuilder()
                    .setPartition(partition)
                    .setBaseOffset(baseOffset)
                    .setRecordCount(records.size())
                    .setErrorCode(0)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            log.info("Appended {} records to {}-{}, baseOffset={}", 
                    records.size(), topic, partition, baseOffset);
            
        } catch (StorageException e) {
            log.error("Storage exception in appendRecords", e);
            
            AppendResponse response = AppendResponse.newBuilder()
                    .setPartition(request.getPartition())
                    .setErrorCode(e.getCode())
                    .setErrorMessage(e.getMessage())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Unexpected exception in appendRecords", e);
            responseObserver.onError(e);
        }
    }
    
    /**
     * Fetch records from the partition log.
     * Called by Consumer Egress Service (Flow 2 Step 4).
     */
    @Override
    public void fetchRecords(FetchRequest request, 
                            StreamObserver<FetchResponse> responseObserver) {
        try {
            log.debug("FetchRecords: topic={}, partition={}, offset={}, maxBytes={}", 
                    request.getTopic(), request.getPartition(), 
                    request.getStartOffset(), request.getMaxBytes());
            
            String topic = request.getTopic();
            int partition = request.getPartition();
            long startOffset = request.getStartOffset();
            
            // Calculate max records based on maxBytes
            int maxRecords = request.getMaxBytes() / 1024; // Rough estimate: 1KB per record
            maxRecords = Math.max(1, Math.min(maxRecords, 10000)); // Between 1 and 10k
            
            // Read from log
            List<LogRecord> records = partitionLog.read(topic, partition, startOffset, maxRecords);
            
            // Convert to proto format
            FetchResponse.Builder responseBuilder = FetchResponse.newBuilder()
                    .setTopic(topic)
                    .setPartition(partition);
            
            long nextOffset = startOffset;
            for (LogRecord record : records) {
                FetchedRecord protoRecord = FetchedRecord.newBuilder()
                        .setKey(record.getKey() != null ? record.getKey() : "")
                        .setValue(com.google.protobuf.ByteString.copyFrom(record.getValue()))
                        .setOffset(record.getOffset())
                        .setTimestamp(record.getTimestamp())
                        .putAllHeaders(record.getHeaders() != null ? record.getHeaders() : Map.of())
                        .build();
                
                responseBuilder.addRecords(protoRecord);
                nextOffset = record.getOffset() + 1;
            }
            
            long hwm = partitionLog.getHighWaterMark(topic, partition);
            responseBuilder.setNextOffset(nextOffset)
                          .setHighWaterMark(hwm)
                          .setErrorCode(0);
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            
            log.debug("Fetched {} records from {}-{}, nextOffset={}", 
                    records.size(), topic, partition, nextOffset);
            
        } catch (StorageException e) {
            log.error("Storage exception in fetchRecords", e);
            
            FetchResponse response = FetchResponse.newBuilder()
                    .setTopic(request.getTopic())
                    .setPartition(request.getPartition())
                    .setErrorCode(e.getCode())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Unexpected exception in fetchRecords", e);
            responseObserver.onError(e);
        }
    }
    
    /**
     * Replicate records from leader to this follower.
     * Called by leader's ReplicationManager.
     */
    @Override
    public void replicateRecords(ReplicationRequest request, 
                                StreamObserver<ReplicationResponse> responseObserver) {
        try {
            log.debug("ReplicateRecords: topic={}, partition={}, records={}, leaderEpoch={}", 
                    request.getTopic(), request.getPartition(), 
                    request.getRecordsCount(), request.getLeaderEpoch());
            
            String topic = request.getTopic();
            int partition = request.getPartition();
            int leaderEpoch = request.getLeaderEpoch();
            
            // Convert proto records to internal format
            List<LogRecord> records = request.getRecordsList().stream()
                    .map(protoRecord -> LogRecord.builder()
                            .key(protoRecord.getKey())
                            .value(protoRecord.getValue().toByteArray())
                            .timestamp(protoRecord.getTimestamp())
                            .headers(new HashMap<>(protoRecord.getHeadersMap()))
                            .leaderEpoch(leaderEpoch)
                            .build())
                    .collect(Collectors.toList());
            
            // Append to local WAL as follower
            partitionLog.append(topic, partition, records, leaderEpoch);
            
            long lastOffset = partitionLog.getLogEndOffset(topic, partition) - 1;
            
            ReplicationResponse response = ReplicationResponse.newBuilder()
                    .setPartition(partition)
                    .setLastOffset(lastOffset)
                    .setSuccess(true)
                    .setErrorCode(0)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            log.trace("Replicated {} records to {}-{}", records.size(), topic, partition);
            
        } catch (Exception e) {
            log.error("Exception in replicateRecords", e);
            
            ReplicationResponse response = ReplicationResponse.newBuilder()
                    .setPartition(request.getPartition())
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.REPLICATION_FAILED.getCode())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    /**
     * Get partition status and offsets.
     * Used for monitoring and ISR tracking.
     */
    @Override
    public void getPartitionStatus(PartitionStatusRequest request, 
                                   StreamObserver<PartitionStatusResponse> responseObserver) {
        try {
            String topic = request.getTopic();
            int partition = request.getPartition();
            
            long leo = partitionLog.getLogEndOffset(topic, partition);
            long hwm = partitionLog.getHighWaterMark(topic, partition);
            List<String> isr = replicationManager.getInSyncReplicas(topic, partition);
            
            PartitionStatusResponse response = PartitionStatusResponse.newBuilder()
                    .setTopic(topic)
                    .setPartition(partition)
                    .setHighWaterMark(hwm)
                    .setLogEndOffset(leo)
                    .setIsLeader(true) // TODO: Determine from partition assignment
                    .setLeaderEpoch(0)  // TODO: Track leader epoch
                    .addAllInSyncReplicas(isr)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Exception in getPartitionStatus", e);
            responseObserver.onError(e);
        }
    }
}
