package com.ltld.app.legacyvault.exception;

// T1: Lỗi thanh toán VNPay và gói thuê bao.
public class PaymentException extends BusinessException {

    public PaymentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static PaymentException signatureInvalid() {
        return new PaymentException(ErrorCode.PAYMENT_SIGNATURE_INVALID, "Invalid VNPay signature.");
    }

    public static PaymentException amountMismatch() {
        return new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "Payment amount does not match the transaction.");
    }
}
