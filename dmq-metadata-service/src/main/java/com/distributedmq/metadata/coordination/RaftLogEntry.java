package com.distributedmq.metadata.coordination;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Raft log entry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaftLogEntry {
    
    /**
     * Term when entry was received by leader
     */
    private long term;
    
    /**
     * Index in the log
     */
    private long index;
    
    /**
     * Command/data to apply to state machine
     * Could be topic creation, partition assignment, etc.
     */
    private Object command;
    
    /**
     * Type of command for processing
     */
    private CommandType commandType;
    
    public enum CommandType {
        CREATE_TOPIC,
        DELETE_TOPIC,
        UPDATE_PARTITION_LEADER,
        REGISTER_BROKER,
        UNREGISTER_BROKER,
        UPDATE_ISR
    }
}
