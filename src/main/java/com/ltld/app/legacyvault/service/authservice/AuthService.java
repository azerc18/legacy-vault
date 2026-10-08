package com.ltld.app.legacyvault.service.authservice;

import com.ltld.app.legacyvault.dto.forgotpassworddto.ResetPasswordRequest;
import com.ltld.app.legacyvault.dto.logindto.LoginRequest;
import com.ltld.app.legacyvault.dto.logindto.LoginResult;
import com.ltld.app.legacyvault.dto.registerdto.RegisterRequest;

public interface AuthService {
     void register(RegisterRequest request);
     LoginResult login(LoginRequest request, String ipAddress, String userAgent);
     void logout(String rawRefreshToken);
     LoginResult refresh(String rawRefreshToken, String ipAddress, String userAgent);
     void resetPassword(ResetPasswordRequest request);
}
