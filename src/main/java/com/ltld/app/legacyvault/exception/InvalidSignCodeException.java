package com.ltld.app.legacyvault.exception;

public class InvalidSignCodeException extends BusinessException {
    public InvalidSignCodeException(String message) {
        this(ErrorCode.INVALID_SIGN_CODE, message);
    }

    // V1: chọn mã cụ thể (SIGN_CODE_EXPIRED, SIGN_CODE_ATTEMPTS_EXHAUSTED) để FE biết phải xin mã mới
    public InvalidSignCodeException(ErrorCode errorCode, String message) {
        super(errorCode, message); // status lấy theo mã
    }
}
