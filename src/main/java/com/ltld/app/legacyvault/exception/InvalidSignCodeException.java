package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class InvalidSignCodeException extends BusinessException {
    public InvalidSignCodeException(String message) {
        this(ErrorCode.INVALID_SIGN_CODE, message);
    }

    // V1: chọn mã cụ thể (SIGN_CODE_EXPIRED, SIGN_CODE_ATTEMPTS_EXHAUSTED) để FE biết phải xin mã mới
    public InvalidSignCodeException(ErrorCode errorCode, String message) {
        super(message, HttpStatus.BAD_REQUEST, errorCode);
    }
}
