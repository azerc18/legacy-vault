package com.ltld.app.legacyvault.exception;

public class RequestNotApprovedException extends RuntimeException {
    public RequestNotApprovedException(String message) {
        super(message);
    }
}