package com.distributedmq.metadata.repository;

import com.distributedmq.metadata.entity.StorageNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StorageNodeRepository extends JpaRepository<StorageNodeEntity, String> {
    
    List<StorageNodeEntity> findByStatus(String status);
    
    @Query("SELECT sn FROM StorageNodeEntity sn WHERE sn.status = 'ALIVE' AND sn.lastHeartbeat > :threshold")
    List<StorageNodeEntity> findHealthyNodes(@Param("threshold") LocalDateTime threshold);
    
    @Query("SELECT sn FROM StorageNodeEntity sn WHERE sn.lastHeartbeat < :threshold AND sn.status IN ('ALIVE', 'SUSPECT')")
    List<StorageNodeEntity> findSuspectNodes(@Param("threshold") LocalDateTime threshold);
}
