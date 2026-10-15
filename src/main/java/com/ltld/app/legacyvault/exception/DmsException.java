package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

// FR-03: lỗi cấu hình / check-in Dead Man's Switch, mang theo HttpStatus chọn tại chỗ ném.
public class DmsException extends RuntimeException {
    private final HttpStatus status;

    public DmsException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }

    public DmsException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
