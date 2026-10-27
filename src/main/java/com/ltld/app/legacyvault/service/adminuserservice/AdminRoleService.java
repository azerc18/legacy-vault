package com.ltld.app.legacyvault.service.adminuserservice;

import com.ltld.app.legacyvault.dto.adminuserdto.AuthorityResponse;
import com.ltld.app.legacyvault.dto.adminuserdto.CreateRoleRequest;
import com.ltld.app.legacyvault.dto.adminuserdto.RoleResponse;
import com.ltld.app.legacyvault.dto.adminuserdto.UpdateRoleRequest;

import java.util.List;
import java.util.UUID;

public interface AdminRoleService {
    List<AuthorityResponse> listAuthorities();
    List<RoleResponse> listRoles();
    RoleResponse createRole(UUID adminId, CreateRoleRequest request);
    RoleResponse updateRole(UUID adminId, String roleCode, UpdateRoleRequest request);
    void deleteRole(UUID adminId, String roleCode);
}