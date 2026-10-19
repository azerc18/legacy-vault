package com.ltld.app.legacyvault.service.adminuserservice;

import com.ltld.app.legacyvault.dto.adminuserdto.AdminUserResponse;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.AdminException;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService{
    private static final Set<String> MANAGEABLE_ROLES = Set.of("LEGAL_VERIFIER", "ADMIN");
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public AdminUserResponse grantRole(UUID adminId, UUID userId, String roleCode) {
        String code = normalizeRole(roleCode);
        User user = lockUser(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AdminException("Chỉ gán role cho tài khoản đang ACTIVE", HttpStatus.CONFLICT);
        }
        boolean has = user.getRoles().stream().anyMatch(r -> code.equals(r.getCode()));
        if (!has) {
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("Role not found: " + code));
            user.getRoles().add(role);
            afterRbacChange(adminId, user, "GRANT " + code);
        }
        return toResponse(user);   // đã có role thì trả 200, không làm gì (idempotent)
    }

    @Override
    @Transactional
    public AdminUserResponse revokeRole(UUID adminId, UUID userId, String roleCode) {
        String code = normalizeRole(roleCode);
        if ("ADMIN".equals(code) && adminId.equals(userId)) {
            throw new AdminException("Không thể tự gỡ quyền ADMIN của chính mình", HttpStatus.CONFLICT);
        }
        User user = lockUser(userId);
        if (user.getRoles().removeIf(r -> code.equals(r.getCode()))) {
            afterRbacChange(adminId, user, "REVOKE " + code);
        }
        return toResponse(user);
    }

    private void afterRbacChange(UUID adminId, User user, String detail) {
        tokenService.revokeAllUserTokens(user.getId(), RevokedReason.ADMIN_REVOKE);
        auditLogService.log(AuditAction.RBAC_CHANGE, AuditResult.SUCCESS, adminId, null,
                "User", user.getId().toString(), detail);
    }

    private User lockUser(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AdminException("Không tìm thấy người dùng", HttpStatus.NOT_FOUND));
    }

    private static String normalizeRole(String roleCode) {
        String code = roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
        if (!MANAGEABLE_ROLES.contains(code)) {
            throw new AdminException("Chỉ quản lý được role LEGAL_VERIFIER và ADMIN", HttpStatus.BAD_REQUEST);
        }
        return code;
    }

    private static AdminUserResponse toResponse(User u) {
        return AdminUserResponse.builder()
                .id(u.getId()).email(u.getEmail()).fullName(u.getFullName()).status(u.getStatus())
                .roles(u.getRoles().stream().map(Role::getCode).sorted().toList())
                .createdAt(u.getCreatedAt()).lastLoginAt(u.getLastLoginAt())
                .build();
    }
}
