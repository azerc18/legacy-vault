package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class UserProfileException extends BusinessException {
    public UserProfileException(String message, HttpStatus status) { super(message, status); }

    public UserProfileException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}