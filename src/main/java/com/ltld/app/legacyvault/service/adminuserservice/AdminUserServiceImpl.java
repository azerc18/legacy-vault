package com.ltld.app.legacyvault.service.adminuserservice;

import com.ltld.app.legacyvault.dto.adminuserdto.AdminUserResponse;
import com.ltld.app.legacyvault.dto.adminuserdto.UpdateUserStatusRequest;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.AdminException;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.dmsservice.ExecutorRescueRule;
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService{
    private static final Set<String> UNMANAGEABLE_ROLES = Set.of("OWNER", "BENEFICIARY", "EXECUTOR");
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final AuditLogService auditLogService;
    private final DmsConfigRepository dmsConfigRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final LegalVerificationRequestRepository legalVerificationRequestRepository;


    private static final int MAX_PAGE_SIZE = 100;

    @Override
    @Transactional
    public AdminUserResponse grantRole(UUID adminId, UUID userId, String roleCode) {
        String code = normalizeRole(roleCode);
        User user = lockUser(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AdminException("Roles can only be granted to ACTIVE accounts", HttpStatus.CONFLICT);
        }
        boolean has = user.getRoles().stream().anyMatch(r -> code.equals(r.getCode()));
        if (!has) {
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new AdminException("Role not found: " + code, HttpStatus.NOT_FOUND));
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
            throw new AdminException("You cannot remove your own ADMIN role", HttpStatus.CONFLICT);
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
                .orElseThrow(() -> new AdminException("User not found", HttpStatus.NOT_FOUND));
    }

    private static String normalizeRole(String roleCode) {
        String code = roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty() || UNMANAGEABLE_ROLES.contains(code)) {
            throw new AdminException("OWNER, BENEFICIARY and EXECUTOR roles cannot be granted or revoked manually",
                    HttpStatus.BAD_REQUEST);
        }
        return code;
    }

    @Override
    @Transactional(readOnly = true)   // bắt buộc: toResponse đọc roles (LAZY)
    public Page<AdminUserResponse> listUsers(String q, String role, String status, int page, int size) {
        String pattern = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        String roleCode = (role == null || role.isBlank()) ? null : role.trim().toUpperCase(Locale.ROOT);
        UserStatus st = (status == null || status.isBlank()) ? null : parseStatus(status);

        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> users = userRepository.searchForAdmin(pattern, roleCode, st, pageable);
        return toResponses(users);
    }

    @Override
    @Transactional
    public AdminUserResponse updateStatus(UUID adminId, UUID userId, UpdateUserStatusRequest req) {
        UserStatus target = parseStatus(req.getStatus());
        if (target == UserStatus.PENDING) {
            throw new AdminException("Only changing between ACTIVE and LOCKED", HttpStatus.BAD_REQUEST);
        }
        if (adminId.equals(userId)) {
            throw new AdminException("Can not change status myself", HttpStatus.CONFLICT);
        }
        User user = lockUser(userId);
        if (user.getStatus() == UserStatus.PENDING) {
            throw new AdminException("Account is not email verified", HttpStatus.CONFLICT);
        }
        if (user.getStatus() == target) {
            return toResponse(user);              // idempotent
        }

        UserStatus old = user.getStatus();
        user.setStatus(target);
        if (target == UserStatus.LOCKED) {
            tokenService.revokeAllUserTokens(userId, RevokedReason.ADMIN_REVOKE);
        } else {
            user.setFailedLoginAttempts(0);       // xóa luôn khóa tạm do sai mật khẩu
            user.setLockedUntil(null);
        }

        String reason = req.getReason() == null || req.getReason().isBlank() ? "" : " reason=" + req.getReason().trim();
        auditLogService.log(AuditAction.RBAC_CHANGE, AuditResult.SUCCESS, adminId, null,
                "User", userId.toString(), "STATUS " + old + "->" + target + reason);
        return toResponse(user);
    }

    private static UserStatus parseStatus(String s) {
        try {
            return UserStatus.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AdminException("Invalid status (PENDING, ACTIVE, LOCKED)", HttpStatus.BAD_REQUEST);
        }
    }

    // Danh sách: lấy DMS + Executor của cả trang trong 2 query
    private Page<AdminUserResponse> toResponses(Page<User> users) {
        List<UUID> ids = users.getContent().stream().map(User::getId).toList();
        if (ids.isEmpty()) return users.map(u -> build(u, null, false));
        Map<UUID, DmsConfig> configs = dmsConfigRepository.findAllByOwnerIdIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getOwner().getId(), c -> c));
        Map<UUID, List<ExecutorAssignment>> active = executorAssignmentRepository.findActiveWithUserByOwnerIdIn(ids)
                .stream().collect(Collectors.groupingBy(a -> a.getOwner().getId()));
        LocalDateTime now = LocalDateTime.now();
        return users.map(u -> {
            DmsConfig c = configs.get(u.getId());
            return build(u, c, ExecutorRescueRule.rescueNeeded(c, active.getOrDefault(u.getId(), List.of()), now,
                    a -> legalVerificationRequestRepository.existsByOwnerIdAndExecutorIdAndCreatedAtAfter(
                            u.getId(), a.getExecutorUser().getId(), a.getActivatedAt())));
        });
    }

    // Một user (sau grant / revoke / updateStatus)
    private AdminUserResponse toResponse(User u) {
        return toResponses(new PageImpl<>(List.of(u))).getContent().get(0);
    }

    private static AdminUserResponse build(User u, DmsConfig config, boolean rescueNeeded) {
        return AdminUserResponse.builder()
                .id(u.getId()).email(u.getEmail()).fullName(u.getFullName()).status(u.getStatus())
                .roles(u.getRoles().stream().map(Role::getCode).sorted().toList())
                .createdAt(u.getCreatedAt()).lastLoginAt(u.getLastLoginAt())
                .dmsStatus(config == null ? null : config.getStatus())
                .executorRescueNeeded(rescueNeeded)
                .build();
    }


}
