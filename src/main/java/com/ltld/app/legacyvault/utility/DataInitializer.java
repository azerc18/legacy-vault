package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.dto.entity.Authority;
import com.ltld.app.legacyvault.dto.entity.Role;
import com.ltld.app.legacyvault.repository.AuthorityRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {
    private final RoleRepository roleRepository;
    private final AuthorityRepository authorityRepository;

    @Bean
    public CommandLineRunner initRolesAndAuthorities() {
        return args -> {
            seedAuthorities();
            seedRoles();
            mapRoleAuthorities();
        };
    }

    // 1. Seed Authorities

    private void seedAuthorities() {
        Map<String, String> defaultAuthorities = new LinkedHashMap<>();
        defaultAuthorities.put("VAULT_CREATE", "Tạo vault");
        defaultAuthorities.put("VAULT_READ", "Xem vault");
        defaultAuthorities.put("VAULT_DELETE", "Xóa vault");
        defaultAuthorities.put("EXECUTOR_MANAGE", "Quản lý executor");
        defaultAuthorities.put("LEGAL_REVIEW", "Xét duyệt pháp lý");
        defaultAuthorities.put("ADMIN_MANAGE", "Quản trị hệ thống");
        defaultAuthorities.put("ASSET_DOWNLOAD", "Tải xuống tài sản");

        defaultAuthorities.forEach((code, desc) -> {
            if (authorityRepository.findByCode(code).isEmpty()) {
                Authority authority = new Authority();
                authority.setCode(code);
                authority.setDescription(desc);
                authorityRepository.save(authority);
            }
        });
    }

    // 2. Seed Roles
    private void seedRoles() {
        Map<String, String[]> defaultRoles = new LinkedHashMap<>();
        // code -> {name, description}
        defaultRoles.put("OWNER", new String[]{"Vault Owner", "Vault Owner"});
        defaultRoles.put("EXECUTOR", new String[]{"Digital Executor", "Digital Executor"});
        defaultRoles.put("BENEFICIARY", new String[]{"Beneficiary", "Beneficiary"});
        defaultRoles.put("LEGAL_VERIFIER", new String[]{"Legal Verifier", "Legal Verifier"});
        defaultRoles.put("ADMIN", new String[]{"System Administrator", "System Administrator"});

        defaultRoles.forEach((code, meta) -> {
            if (roleRepository.findByCode(code).isEmpty()) {
                Role role = new Role();
                role.setCode(code);
                role.setName(meta[0]);
                role.setDescription(meta[1]);
                roleRepository.save(role);
            }
        });
    }

    // 3. Map role_authorities
    private void mapRoleAuthorities() {
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("OWNER", List.of("VAULT_CREATE", "VAULT_READ", "VAULT_DELETE", "EXECUTOR_MANAGE"));
        mapping.put("EXECUTOR", List.of("VAULT_READ", "ASSET_DOWNLOAD"));
        mapping.put("BENEFICIARY", List.of("VAULT_READ", "ASSET_DOWNLOAD"));
        mapping.put("LEGAL_VERIFIER", List.of("VAULT_READ", "LEGAL_REVIEW"));
        mapping.put("ADMIN", List.of(
                "VAULT_CREATE", "VAULT_READ", "VAULT_DELETE",
                "EXECUTOR_MANAGE", "LEGAL_REVIEW", "ADMIN_MANAGE", "ASSET_DOWNLOAD"
        ));

        mapping.forEach((roleCode, authCodes) -> {
            Role role = roleRepository.findByCode(roleCode).orElseThrow(
                    () -> new IllegalStateException("Role not found: " + roleCode));

            Set<Authority> authorities = authCodes.stream()
                    .map(code -> authorityRepository.findByCode(code)
                            .orElseThrow(() -> new IllegalStateException("Authority not found: " + code)))
                    .collect(Collectors.toSet());

            role.setAuthorities(authorities);
            roleRepository.save(role);

        });
    }
}
