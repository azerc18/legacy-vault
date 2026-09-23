package com.ltld.app.legacyvault.service.verificationservice;

import com.ltld.app.legacyvault.dto.otpdto.SendOtpRequest;
import com.ltld.app.legacyvault.dto.otpdto.VerifyOtpRequest;

public interface VerificationTokenService {
    void sendOtp(SendOtpRequest request);
    void verifyEmail(VerifyOtpRequest request);
}
