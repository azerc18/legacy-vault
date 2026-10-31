package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

// FR-03 / F2: lỗi cấu hình / check-in Dead Man's Switch, mang theo HttpStatus và errorCode.
public class DmsException extends BusinessException {

    public static final String CHECKIN_TOKEN_INVALID = "CHECKIN_TOKEN_INVALID";

    private final String errorCode;

    public DmsException(String message) {
        this(message, HttpStatus.BAD_REQUEST, null);      // giữ mặc định 400 như cũ
    }

    public DmsException(String message, HttpStatus status) {
        this(message, status, null);
    }

    public DmsException(String message, HttpStatus status, String errorCode) {
        super(message, status);
        this.errorCode = errorCode;
    }

    public static DmsException checkinTokenInvalid(String message) {
        return new DmsException(message, HttpStatus.BAD_REQUEST, CHECKIN_TOKEN_INVALID);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
