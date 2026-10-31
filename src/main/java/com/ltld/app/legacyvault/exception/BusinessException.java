package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final ErrorCode errorCode;

    // Cũ: giữ nguyên chữ ký
    public BusinessException(String message, HttpStatus status) {
        this(message, status, ErrorCode.fromStatus(status));
    }

    // Chọn status khác với mặc định của mã (ít dùng)
    public BusinessException(String message, HttpStatus status, ErrorCode errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    // Mới, dùng chủ yếu từ giờ: status lấy theo mã
    public BusinessException(ErrorCode errorCode, String message) {
        this(message, errorCode.getStatus(), errorCode);
    }

    public HttpStatus getStatus() { return status; }
    public ErrorCode getErrorCode() { return errorCode; }
}