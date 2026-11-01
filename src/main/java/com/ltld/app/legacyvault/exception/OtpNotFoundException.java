package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class OtpNotFoundException extends BusinessException {
    public OtpNotFoundException(String message) {
        super(message, HttpStatus.BAD_REQUEST,ErrorCode.OTP_NOT_FOUND
        );
    }
}
