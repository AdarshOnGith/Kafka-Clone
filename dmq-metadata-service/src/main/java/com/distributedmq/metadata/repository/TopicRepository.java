package com.distributedmq.metadata.repository;

import com.distributedmq.metadata.entity.TopicEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TopicRepository extends JpaRepository<TopicEntity, Integer> {
    
    Optional<TopicEntity> findByTopicName(String topicName);
    
    boolean existsByTopicName(String topicName);
}
