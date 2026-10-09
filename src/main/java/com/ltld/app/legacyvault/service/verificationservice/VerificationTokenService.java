package com.ltld.app.legacyvault.service.verificationservice;

import com.ltld.app.legacyvault.dto.forgotpassworddto.ForgotPasswordRequest;
import com.ltld.app.legacyvault.dto.otpdto.SendOtpRequest;
import com.ltld.app.legacyvault.dto.otpdto.VerifyOtpRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.TokenType;

public interface VerificationTokenService {
    void sendOtp(SendOtpRequest request);
    void issueOtp(User user, TokenType type);
    void verifyEmail(VerifyOtpRequest request);
    void sendPasswordResetOtp(ForgotPasswordRequest request);
    void consumeOtp(User user, TokenType type, String otp, AuditAction failAction);
}
