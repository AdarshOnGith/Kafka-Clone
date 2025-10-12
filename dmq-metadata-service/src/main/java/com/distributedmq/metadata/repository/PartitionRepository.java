package com.distributedmq.metadata.repository;

import com.distributedmq.metadata.entity.PartitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartitionRepository extends JpaRepository<PartitionEntity, Integer> {
    
    List<PartitionEntity> findByTopicId(Integer topicId);
    
    Optional<PartitionEntity> findByTopicIdAndPartitionNumber(Integer topicId, Integer partitionNumber);
    
    @Query("SELECT p FROM PartitionEntity p WHERE p.leaderNodeId = :nodeId")
    List<PartitionEntity> findByLeaderNodeId(@Param("nodeId") String nodeId);
    
    @Query("SELECT COUNT(p) FROM PartitionEntity p WHERE p.topicId = :topicId")
    int countByTopicId(@Param("topicId") Integer topicId);
}
