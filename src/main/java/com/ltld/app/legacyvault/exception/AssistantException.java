package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class AssistantException extends BusinessException {
    public AssistantException(String message, HttpStatus status) {
        super(message, status);
    }

    public AssistantException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
