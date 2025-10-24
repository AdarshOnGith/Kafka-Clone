package com.distributedmq.metadata.coordination;

/**
 * Raft command types for metadata operations
 */
public enum RaftCommandType {
    CREATE_TOPIC,
    DELETE_TOPIC,
    UPDATE_PARTITION_LEADER,
    REGISTER_BROKER,
    UNREGISTER_BROKER,
    UPDATE_ISR,
    CREATE_CONSUMER_GROUP,
    DELETE_CONSUMER_GROUP,
    UPDATE_CONSUMER_OFFSET
}