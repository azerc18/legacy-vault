package com.ltld.app.legacyvault.exception;

// T1: ném khi người dùng gói FREE chạm giới hạn (số lượng vault, beneficiary, dung lượng, legacy messages).
public class PlanLimitReachedException extends BusinessException {

    public PlanLimitReachedException(String message) {
        super(ErrorCode.PLAN_LIMIT_REACHED, message);
    }
}
