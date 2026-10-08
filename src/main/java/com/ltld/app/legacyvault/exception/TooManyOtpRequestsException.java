package com.ltld.app.legacyvault.exception;

public class TooManyOtpRequestsException extends RuntimeException {
    private final long retryAfterSeconds;
    public TooManyOtpRequestsException(long s) { super("Too many OTP requests"); this.retryAfterSeconds = s; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
