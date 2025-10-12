package com.distributedmq.common.exception;

/**
 * Exception thrown when storage-related operations fail.
 */
public class StorageException extends DMQException {
    
    public StorageException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public StorageException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public StorageException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
    
    public StorageException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
