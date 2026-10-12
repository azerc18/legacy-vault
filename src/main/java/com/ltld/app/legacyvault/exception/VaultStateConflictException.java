package com.ltld.app.legacyvault.exception;

public class VaultStateConflictException extends RuntimeException {
    public VaultStateConflictException(String message) { super(message); }
}