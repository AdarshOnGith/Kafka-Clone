package com.distributedmq.metadata.grpc;

import com.distributedmq.common.model.PartitionMetadata;
import com.distributedmq.common.proto.*;
import com.distributedmq.metadata.service.ConsumerOffsetService;
import com.distributedmq.metadata.service.PartitionMetadataService;
import com.distributedmq.metadata.service.StorageNodeService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * gRPC server implementation for Metadata Service.
 * Provides high-performance inter-service communication.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class MetadataServiceGrpcServer extends MetadataServiceGrpc.MetadataServiceImplBase {
    
    private final PartitionMetadataService partitionService;
    private final ConsumerOffsetService offsetService;
    private final StorageNodeService nodeService;
    
    @Override
    public void getPartitionMetadata(PartitionMetadataRequest request,
                                    StreamObserver<PartitionMetadataResponse> responseObserver) {
        try {
            log.debug("gRPC: getPartitionMetadata - topic={}, partition={}", 
                    request.getTopic(), request.getPartition());
            
            PartitionMetadata metadata = partitionService.getPartitionMetadata(
                    request.getTopic(),
                    request.getPartition()
            );
            
            PartitionMetadataResponse response = PartitionMetadataResponse.newBuilder()
                    .setTopic(metadata.getTopicName())
                    .setPartition(metadata.getPartitionNumber())
                    .setLeaderNodeId(metadata.getLeaderNodeId())
                    .setLeaderNodeAddress(metadata.getLeaderNodeAddress())
                    .addAllReplicas(metadata.getInSyncReplicas())
                    .addAllInSyncReplicas(metadata.getInSyncReplicas())
                    .setHighWaterMark(metadata.getHighWaterMark() != null ? metadata.getHighWaterMark() : 0L)
                    .setLeaderEpoch(metadata.getLeaderEpoch() != null ? metadata.getLeaderEpoch() : 0)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error in getPartitionMetadata", e);
            responseObserver.onError(e);
        }
    }
    
    @Override
    public void getTopicMetadata(TopicMetadataRequest request,
                                StreamObserver<TopicMetadataResponse> responseObserver) {
        try {
            log.debug("gRPC: getTopicMetadata - topic={}", request.getTopic());
            
            List<PartitionMetadata> partitions = partitionService.getTopicMetadata(request.getTopic());
            
            TopicMetadataResponse.Builder responseBuilder = TopicMetadataResponse.newBuilder()
                    .setTopic(request.getTopic());
            
            for (PartitionMetadata metadata : partitions) {
                PartitionMetadataResponse partitionResponse = PartitionMetadataResponse.newBuilder()
                        .setTopic(metadata.getTopicName())
                        .setPartition(metadata.getPartitionNumber())
                        .setLeaderNodeId(metadata.getLeaderNodeId())
                        .setLeaderNodeAddress(metadata.getLeaderNodeAddress())
                        .addAllInSyncReplicas(metadata.getInSyncReplicas())
                        .setLeaderEpoch(metadata.getLeaderEpoch() != null ? metadata.getLeaderEpoch() : 0)
                        .build();
                
                responseBuilder.addPartitions(partitionResponse);
            }
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error in getTopicMetadata", e);
            responseObserver.onError(e);
        }
    }
    
    @Override
    public void updatePartitionLeader(UpdateLeaderRequest request,
                                     StreamObserver<UpdateLeaderResponse> responseObserver) {
        try {
            log.info("gRPC: updatePartitionLeader - topic={}, partition={}, newLeader={}", 
                    request.getTopic(), request.getPartition(), request.getNewLeaderNodeId());
            
            partitionService.updatePartitionLeader(
                    request.getTopic(),
                    request.getPartition(),
                    request.getNewLeaderNodeId(),
                    request.getNewLeaderAddress(),
                    request.getNewLeaderEpoch()
            );
            
            UpdateLeaderResponse response = UpdateLeaderResponse.newBuilder()
                    .setSuccess(true)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error in updatePartitionLeader", e);
            
            UpdateLeaderResponse response = UpdateLeaderResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(5001)
                    .setErrorMessage(e.getMessage())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void getConsumerOffset(GetOffsetRequest request,
                                 StreamObserver<GetOffsetResponse> responseObserver) {
        try {
            log.debug("gRPC: getConsumerOffset - group={}, topic={}, partition={}", 
                    request.getGroupId(), request.getTopic(), request.getPartition());
            
            var offset = offsetService.getConsumerOffset(
                    request.getGroupId(),
                    request.getTopic(),
                    request.getPartition()
            );
            
            GetOffsetResponse response = GetOffsetResponse.newBuilder()
                    .setGroupId(offset.getGroupId())
                    .setTopic(offset.getTopicName())
                    .setPartition(offset.getPartition())
                    .setOffset(offset.getOffset())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error in getConsumerOffset", e);
            responseObserver.onError(e);
        }
    }
    
    @Override
    public void commitConsumerOffset(CommitOffsetRequest request,
                                    StreamObserver<CommitOffsetResponse> responseObserver) {
        try {
            log.debug("gRPC: commitConsumerOffset - group={}, topic={}, partition={}, offset={}", 
                    request.getGroupId(), request.getTopic(), request.getPartition(), request.getOffset());
            
            var offset = com.distributedmq.common.model.ConsumerOffset.builder()
                    .groupId(request.getGroupId())
                    .topicName(request.getTopic())
                    .partition(request.getPartition())
                    .offset(request.getOffset())
                    .leaderEpoch(request.getLeaderEpoch())
                    .metadata(request.getMetadata())
                    .timestamp(LocalDateTime.now())
                    .build();
            
            offsetService.commitOffset(offset);
            
            CommitOffsetResponse response = CommitOffsetResponse.newBuilder()
                    .setSuccess(true)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error in commitConsumerOffset", e);
            
            CommitOffsetResponse response = CommitOffsetResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(3005)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void registerStorageNode(RegisterNodeRequest request,
                                   StreamObserver<RegisterNodeResponse> responseObserver) {
        try {
            log.info("gRPC: registerStorageNode - nodeId={}, host={}, port={}", 
                    request.getNodeId(), request.getHost(), request.getPort());
            
            nodeService.registerNode(
                    request.getNodeId(),
                    request.getHost(),
                    request.getPort(),
                    request.getRackId()
            );
            
            RegisterNodeResponse response = RegisterNodeResponse.newBuilder()
                    .setSuccess(true)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error in registerStorageNode", e);
            responseObserver.onError(e);
        }
    }
    
    @Override
    public void nodeHeartbeat(HeartbeatRequest request,
                             StreamObserver<HeartbeatResponse> responseObserver) {
        try {
            nodeService.processHeartbeat(request.getNodeId());
            
            HeartbeatResponse response = HeartbeatResponse.newBuilder()
                    .setSuccess(true)
                    .setServerTimestamp(System.currentTimeMillis())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error in nodeHeartbeat", e);
            responseObserver.onError(e);
        }
    }
}
