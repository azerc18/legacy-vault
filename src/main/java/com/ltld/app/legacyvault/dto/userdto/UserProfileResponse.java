package com.ltld.app.legacyvault.dto.userdto;

import com.ltld.app.legacyvault.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class UserProfileResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private UserStatus status;
    private List<String> roles;
    private Instant createdAt;
    private Instant lastLoginAt;
}