package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class ExecutorAssignmentNotFoundException extends BusinessException {
    public ExecutorAssignmentNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
