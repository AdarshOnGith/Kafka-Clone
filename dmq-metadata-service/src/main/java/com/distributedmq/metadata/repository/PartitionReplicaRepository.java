package com.distributedmq.metadata.repository;

import com.distributedmq.metadata.entity.PartitionReplicaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartitionReplicaRepository extends JpaRepository<PartitionReplicaEntity, Integer> {
    
    List<PartitionReplicaEntity> findByPartitionId(Integer partitionId);
    
    @Query("SELECT pr FROM PartitionReplicaEntity pr WHERE pr.partitionId = :partitionId AND pr.isInSync = true")
    List<PartitionReplicaEntity> findInSyncReplicasByPartitionId(@Param("partitionId") Integer partitionId);
    
    @Query("SELECT pr FROM PartitionReplicaEntity pr WHERE pr.nodeId = :nodeId")
    List<PartitionReplicaEntity> findByNodeId(@Param("nodeId") String nodeId);
}
