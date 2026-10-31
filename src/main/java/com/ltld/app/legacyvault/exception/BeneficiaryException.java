package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class BeneficiaryException extends BusinessException {
    public BeneficiaryException(String message, HttpStatus status) {
        super(message, status);
    }

    public BeneficiaryException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}