package com.ltld.app.legacyvault.service.adminuserservice;

import com.ltld.app.legacyvault.dto.adminuserdto.AdminUserResponse;

import java.util.UUID;

public interface AdminUserService {
    AdminUserResponse grantRole(UUID adminId, UUID userId, String roleCode);
    AdminUserResponse revokeRole(UUID adminId, UUID userId, String roleCode);
}
