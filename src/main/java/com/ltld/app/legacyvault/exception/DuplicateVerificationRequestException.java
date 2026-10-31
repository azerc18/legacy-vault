package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class DuplicateVerificationRequestException extends BusinessException {
    public DuplicateVerificationRequestException(String message) {
        super(message, HttpStatus.CONFLICT,ErrorCode.DUPLICATE_VERIFICATION_REQUEST);
    }
}
