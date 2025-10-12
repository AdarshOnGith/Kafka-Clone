package com.distributedmq.common.exception;

/**
 * Exception thrown when metadata operations fail.
 */
public class MetadataException extends DMQException {
    
    public MetadataException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public MetadataException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public MetadataException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
    
    public MetadataException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
