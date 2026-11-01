package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class TooManyAttemptsException extends BusinessException {
    public TooManyAttemptsException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_ATTEMPTS
        );
    }
}
