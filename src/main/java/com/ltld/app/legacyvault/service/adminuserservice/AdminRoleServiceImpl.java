package com.ltld.app.legacyvault.service.adminuserservice;

import com.ltld.app.legacyvault.dto.adminuserdto.AuthorityResponse;
import com.ltld.app.legacyvault.dto.adminuserdto.CreateRoleRequest;
import com.ltld.app.legacyvault.dto.adminuserdto.RoleResponse;
import com.ltld.app.legacyvault.dto.adminuserdto.UpdateRoleRequest;
import com.ltld.app.legacyvault.entity.Authority;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.exception.AdminException;
import com.ltld.app.legacyvault.repository.AuthorityRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/** FR-20: Admin quản lý role và gán quyền cho role. Authority chỉ xem, không tạo mới (code mới dùng được). */
@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl implements AdminRoleService {

    // Code gọi các role này theo tên (findByCode, hasRole) nên không được xóa.
    static final Set<String> SYSTEM_ROLES = Set.of("OWNER", "EXECUTOR", "BENEFICIARY", "LEGAL_VERIFIER", "ADMIN");
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String ADMIN_MANAGE = "ADMIN_MANAGE";

    private final RoleRepository roleRepository;
    private final AuthorityRepository authorityRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<AuthorityResponse> listAuthorities() {
        return authorityRepository.findAll(Sort.by("code")).stream()
                .map(a -> AuthorityResponse.builder().code(a.getCode()).description(a.getDescription()).build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)   // authorities là LAZY
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll(Sort.by("code")).stream()
                .map(AdminRoleServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public RoleResponse createRole(UUID adminId, CreateRoleRequest req) {
        String code = req.getCode().trim();
        if (roleRepository.findByCode(code).isPresent()) {
            throw new AdminException("Role already exists: " + code, HttpStatus.CONFLICT);
        }
        Role role = roleRepository.save(Role.builder()
                .code(code)
                .name(req.getName().trim())
                .description(trimToNull(req.getDescription()))
                .authorities(resolveAuthorities(req.getAuthorities()))
                .build());

        audit(adminId, role.getId(), "CREATE ROLE " + code + " " + authorityCodes(role));
        return toResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse updateRole(UUID adminId, String roleCode, UpdateRoleRequest req) {
        Role role = findRole(roleCode);
        Set<Authority> authorities = resolveAuthorities(req.getAuthorities());

        // Giữ ADMIN_MANAGE cho role ADMIN, nếu không sẽ không còn ai quản trị được hệ thống.
        if (ADMIN_ROLE.equals(role.getCode())
                && authorities.stream().noneMatch(a -> ADMIN_MANAGE.equals(a.getCode()))) {
            throw new AdminException("The ADMIN role must keep the ADMIN_MANAGE authority", HttpStatus.CONFLICT);
        }

        role.setName(req.getName().trim());
        role.setDescription(trimToNull(req.getDescription()));
        role.getAuthorities().clear();
        role.getAuthorities().addAll(authorities);

        audit(adminId, role.getId(), "UPDATE ROLE " + role.getCode() + " " + authorityCodes(role));
        return toResponse(role);
    }

    @Override
    @Transactional
    public void deleteRole(UUID adminId, String roleCode) {
        Role role = findRole(roleCode);
        if (SYSTEM_ROLES.contains(role.getCode())) {
            throw new AdminException("Cannot delete a system role: " + role.getCode(), HttpStatus.CONFLICT);
        }
        if (userRepository.existsByRoles_Code(role.getCode())) {
            throw new AdminException("The role is assigned to users; remove it from them before deleting",
                    HttpStatus.CONFLICT);
        }
        UUID roleId = role.getId();
        roleRepository.delete(role);
        audit(adminId, roleId, "DELETE ROLE " + role.getCode());
    }

    private Role findRole(String roleCode) {
        String code = roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new AdminException("Role not found: " + code, HttpStatus.NOT_FOUND));
    }

    private Set<Authority> resolveAuthorities(List<String> codes) {
        Set<String> wanted = codes.stream()
                .filter(Objects::nonNull)
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
        if (wanted.isEmpty()) {
            throw new AdminException("A role must have at least one authority", HttpStatus.BAD_REQUEST);
        }

        List<Authority> found = authorityRepository.findByCodeIn(wanted);
        if (found.size() != wanted.size()) {
            found.forEach(a -> wanted.remove(a.getCode()));
            throw new AdminException("Authorities not found: " + String.join(", ", wanted), HttpStatus.BAD_REQUEST);
        }
        return new HashSet<>(found);
    }

    private void audit(UUID adminId, UUID roleId, String detail) {
        auditLogService.log(AuditAction.RBAC_CHANGE, AuditResult.SUCCESS, adminId, null,
                "Role", roleId.toString(), detail);
    }

    private static List<String> authorityCodes(Role role) {
        return role.getAuthorities().stream().map(Authority::getCode).sorted().toList();
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static RoleResponse toResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .system(SYSTEM_ROLES.contains(role.getCode()))
                .authorities(authorityCodes(role))
                .build();
    }
}