package com.distributedmq.metadata.repository;

import com.distributedmq.metadata.entity.ConsumerOffsetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConsumerOffsetRepository extends JpaRepository<ConsumerOffsetEntity, Integer> {
    
    @Query("SELECT co FROM ConsumerOffsetEntity co WHERE co.groupId = :groupId AND co.topicId = :topicId AND co.partitionNumber = :partitionNumber")
    Optional<ConsumerOffsetEntity> findByGroupIdAndTopicIdAndPartitionNumber(
            @Param("groupId") String groupId,
            @Param("topicId") Integer topicId,
            @Param("partitionNumber") Integer partitionNumber
    );
    
    List<ConsumerOffsetEntity> findByGroupIdAndTopicId(String groupId, Integer topicId);
    
    List<ConsumerOffsetEntity> findByGroupId(String groupId);
    
    @Modifying
    @Query("DELETE FROM ConsumerOffsetEntity co WHERE co.expireTimestamp IS NOT NULL AND co.expireTimestamp < :now")
    int deleteExpiredOffsets(@Param("now") LocalDateTime now);
}
