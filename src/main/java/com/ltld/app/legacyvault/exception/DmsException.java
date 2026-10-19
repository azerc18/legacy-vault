package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

// FR-03: lỗi cấu hình / check-in Dead Man's Switch, mang theo HttpStatus chọn tại chỗ ném.
public class DmsException extends BusinessException  {
    public DmsException(String message) {
        super(message, HttpStatus.BAD_REQUEST);      // giữ mặc định 400 như cũ
    }

    public DmsException(String message, HttpStatus status) {
        super(message, status);
    }
}
