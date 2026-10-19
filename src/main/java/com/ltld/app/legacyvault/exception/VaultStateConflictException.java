package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class VaultStateConflictException extends BusinessException {
    public VaultStateConflictException(String message) { super(message, HttpStatus.CONFLICT); }
}