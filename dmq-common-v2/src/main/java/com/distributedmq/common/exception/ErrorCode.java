package com.distributedmq.common.exception;

/**
 * Error codes for categorizing different types of failures.
 * Error codes are organized by category:
 * - 1xxx: Client errors
 * - 2xxx: Storage errors
 * - 3xxx: Metadata errors
 * - 4xxx: Network errors
 * - 5xxx: Controller/Coordination errors
 */
public enum ErrorCode {
    
    // Client errors (1xxx)
    INVALID_REQUEST(1001, "Invalid request parameters"),
    TOPIC_NOT_FOUND(1002, "Topic does not exist"),
    PARTITION_NOT_FOUND(1003, "Partition does not exist"),
    INVALID_PARTITION(1004, "Invalid partition number"),
    INVALID_OFFSET(1005, "Invalid offset value"),
    CONSUMER_GROUP_NOT_FOUND(1006, "Consumer group does not exist"),
    AUTHENTICATION_FAILED(1007, "Authentication failed"),
    AUTHORIZATION_FAILED(1008, "Not authorized to perform this operation"),
    RATE_LIMIT_EXCEEDED(1009, "Rate limit exceeded"),
    
    // Storage errors (2xxx)
    STORAGE_UNAVAILABLE(2001, "Storage service is unavailable"),
    WRITE_FAILED(2002, "Failed to write records to storage"),
    READ_FAILED(2003, "Failed to read records from storage"),
    REPLICATION_FAILED(2004, "Replication to followers failed"),
    NOT_LEADER_FOR_PARTITION(2005, "This node is not the leader for the partition"),
    OFFSET_OUT_OF_RANGE(2006, "Requested offset is out of range"),
    LOG_CORRUPTION(2007, "Detected corruption in the commit log"),
    DISK_FULL(2008, "Storage disk is full"),
    
    // Metadata errors (3xxx)
    METADATA_UNAVAILABLE(3001, "Metadata service is unavailable"),
    LEADER_NOT_AVAILABLE(3002, "No leader available for partition"),
    STALE_METADATA(3003, "Metadata is stale, please retry"),
    METADATA_UPDATE_FAILED(3004, "Failed to update metadata"),
    OFFSET_COMMIT_FAILED(3005, "Failed to commit consumer offset"),
    
    // Network errors (4xxx)
    NETWORK_TIMEOUT(4001, "Network operation timed out"),
    SERVICE_UNAVAILABLE(4002, "Service temporarily unavailable"),
    CONNECTION_FAILED(4003, "Failed to establish connection"),
    
    // Controller/Coordination errors (5xxx)
    LEADER_ELECTION_FAILED(5001, "Leader election failed"),
    NO_SUITABLE_REPLICA(5002, "No suitable replica found for leadership"),
    REBALANCE_IN_PROGRESS(5003, "Consumer group rebalance in progress"),
    COORDINATION_ERROR(5004, "Distributed coordination error"),
    
    // Unknown errors
    UNKNOWN_ERROR(9999, "Unknown error occurred");
    
    private final int code;
    private final String message;
    
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return UNKNOWN_ERROR;
    }
}
