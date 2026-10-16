package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

/** FR-06: lỗi khi Owner quản lý Executor; mã HTTP được chọn tại nơi ném (400/404/409/422). */
public class ExecutorManagementException extends RuntimeException {
    private final HttpStatus status;

    public ExecutorManagementException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
