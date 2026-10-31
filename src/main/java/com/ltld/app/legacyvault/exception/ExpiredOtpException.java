package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class ExpiredOtpException extends BusinessException {
    public ExpiredOtpException(String message) {
        super(message, HttpStatus.BAD_REQUEST,ErrorCode.OTP_EXPIRED
        );
    }
}
