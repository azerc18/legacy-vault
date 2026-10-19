package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class AdminException extends BusinessException {
    public AdminException(String message, HttpStatus status) { super(message, status); }
}
