package com.ltld.app.legacyvault.service.verificationservice;

import com.ltld.app.legacyvault.dto.otpdto.SendOtpRequest;
import com.ltld.app.legacyvault.dto.otpdto.VerifyOtpRequest;
import com.ltld.app.legacyvault.entity.User;

public interface VerificationTokenService {
    void sendOtp(SendOtpRequest request);
    void issueOtp(User user);
    void verifyEmail(VerifyOtpRequest request);
}
