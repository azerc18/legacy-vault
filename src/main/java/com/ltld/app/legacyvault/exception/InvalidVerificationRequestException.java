package com.ltld.app.legacyvault.exception;

// FR-09: yêu cầu xác minh không hợp lệ (vd. các Vault thuộc nhiều Owner khác nhau)
public class InvalidVerificationRequestException extends RuntimeException {
    public InvalidVerificationRequestException(String message) {
        super(message);
    }
}
