package com.ltld.app.legacyvault.exception;

public class TooManyOtpRequestsException extends BusinessException {
    private final long retryAfterSeconds;
    public TooManyOtpRequestsException(long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_OTP_REQUESTS,
                "Too many OTP requests. Please try again in " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
