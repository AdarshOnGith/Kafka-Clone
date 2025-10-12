package com.distributedmq.common.exception;

/**
 * Base exception for all Distributed Message Queue errors.
 * All custom exceptions should extend this base class.
 */
public class DMQException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    public DMQException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public DMQException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public DMQException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public DMQException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    public int getCode() {
        return errorCode.getCode();
    }
}
