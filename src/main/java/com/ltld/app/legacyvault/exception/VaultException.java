package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class VaultException extends BusinessException {
    public VaultException(String message) {
        super(message, HttpStatus.BAD_REQUEST);      // giữ mặc định 400 như cũ
    }

    public VaultException(String message, HttpStatus status) {
        super(message, status);
    }
}
