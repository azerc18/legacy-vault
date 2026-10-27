package com.ltld.app.legacyvault.utility;

public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static final String REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$";
    public static final String MESSAGE =
            "Password must be 8-72 characters and include uppercase, lowercase, a number and a special character.";
}
