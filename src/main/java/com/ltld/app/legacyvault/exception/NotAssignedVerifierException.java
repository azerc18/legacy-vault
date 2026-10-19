package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class NotAssignedVerifierException extends BusinessException {
    public NotAssignedVerifierException(String message) { super(message, HttpStatus.FORBIDDEN); }
}