package com.distributedmq.metadata.repository;

import com.distributedmq.metadata.entity.ConsumerGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsumerGroupRepository extends JpaRepository<ConsumerGroupEntity, String> {
    
    List<ConsumerGroupEntity> findByGroupState(String groupState);
}
