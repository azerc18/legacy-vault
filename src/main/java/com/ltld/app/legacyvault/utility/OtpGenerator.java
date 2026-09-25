package com.ltld.app.legacyvault.utility;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class OtpGenerator {
    private static final int OTP_LENGTH = 6;
    private static final int OTP_BOUND = (int) Math.pow(10, OTP_LENGTH);

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate(){
        int code = secureRandom.nextInt(OTP_BOUND);
        return String.format("%0" + OTP_LENGTH + "d", code);
    }
}
