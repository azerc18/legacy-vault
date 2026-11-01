package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class ExecutorNotActiveException extends BusinessException {
    public ExecutorNotActiveException(String message) {
        super(message, HttpStatus.FORBIDDEN,ErrorCode.EXECUTOR_NOT_ACTIVE);
    }
}
