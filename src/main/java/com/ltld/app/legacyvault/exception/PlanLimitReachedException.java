package com.ltld.app.legacyvault.exception;

// T1: ném khi người dùng gói FREE chạm giới hạn (số lượng vault, beneficiary, dung lượng, legacy messages).
public class PlanLimitReachedException extends BusinessException {

    private final PlanFeature feature;

    public PlanLimitReachedException(PlanFeature feature, String message) {
        super(ErrorCode.PLAN_LIMIT_REACHED, message);
        this.feature = feature;
    }

    public PlanLimitReachedException(String message) {
        this(null, message);
    }

    public PlanFeature getFeature() {
        return feature;
    }
}
