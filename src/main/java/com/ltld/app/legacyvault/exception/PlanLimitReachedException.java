package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

// T1: ném khi người dùng gói FREE chạm giới hạn (số lượng vault, beneficiary, dung lượng, legacy messages).
public class PlanLimitReachedException extends BusinessException {

    public static final String ERROR_CODE = "PLAN_LIMIT_REACHED";

    private final String errorCode;

    public PlanLimitReachedException(String message) {
        this(message, ERROR_CODE);
    }

    public PlanLimitReachedException(String message, String errorCode) {
        super(message, HttpStatus.FORBIDDEN);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
