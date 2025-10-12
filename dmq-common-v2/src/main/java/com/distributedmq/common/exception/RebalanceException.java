package com.distributedmq.common.exception;

/**
 * Exception thrown when rebalancing operations fail.
 */
public class RebalanceException extends DMQException {
    
    public RebalanceException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public RebalanceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public RebalanceException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
