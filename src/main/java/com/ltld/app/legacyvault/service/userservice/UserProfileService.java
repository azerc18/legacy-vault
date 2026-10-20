package com.ltld.app.legacyvault.service.userservice;

import com.ltld.app.legacyvault.dto.userdto.ChangePasswordRequest;
import com.ltld.app.legacyvault.dto.userdto.UpdateProfileRequest;
import com.ltld.app.legacyvault.dto.userdto.UserProfileResponse;

import java.util.UUID;

public interface UserProfileService {
    UserProfileResponse getMe(UUID userId);
    UserProfileResponse updateMe(UUID userId, UpdateProfileRequest request);
    void changePassword(UUID userId, ChangePasswordRequest request);
}