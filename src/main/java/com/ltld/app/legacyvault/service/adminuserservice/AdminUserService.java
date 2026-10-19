package com.ltld.app.legacyvault.service.adminuserservice;

import com.ltld.app.legacyvault.dto.adminuserdto.AdminUserResponse;
import com.ltld.app.legacyvault.dto.adminuserdto.UpdateUserStatusRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface AdminUserService {
    AdminUserResponse grantRole(UUID adminId, UUID userId, String roleCode);
    AdminUserResponse revokeRole(UUID adminId, UUID userId, String roleCode);
    Page<AdminUserResponse> listUsers(String q, String role, String status, int page, int size);
    AdminUserResponse updateStatus(UUID adminId, UUID userId, UpdateUserStatusRequest request);
}
