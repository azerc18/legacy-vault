package com.ltld.app.legacyvault.service.adminuserservice;

import com.ltld.app.legacyvault.dto.adminuserdto.AdminUserResponse;
import com.ltld.app.legacyvault.dto.adminuserdto.UpdateUserStatusRequest;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.AdminException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;
    @Mock private TokenService tokenService;
    @Mock private AuditLogService auditLogService;
    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private LegalVerificationRequestRepository legalVerificationRequestRepository;

    @InjectMocks
    private AdminUserServiceImpl service;

    private UUID adminId;
    private UUID userId;
    private User user;
    private Role verifierRole;
    private Role ownerRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        userId = UUID.randomUUID();
        verifierRole = Role.builder().id(UUID.randomUUID()).code("LEGAL_VERIFIER").name("Legal Verifier").build();
        ownerRole = Role.builder().id(UUID.randomUUID()).code("OWNER").name("Vault Owner").build();
        adminRole = Role.builder().id(UUID.randomUUID()).code("ADMIN").name("System Administrator").build();
        user = User.builder()
                .id(userId).email("user@example.test").fullName("Test User")
                .passwordHash("hash").status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(ownerRole)))
                .build();
    }

    private static void assertAdminError(ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AdminException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(expected));
    }

    private void verifyNoRbacSideEffects() {
        verify(tokenService, never()).revokeAllUserTokens(any(), any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Nested
    @DisplayName("A2 grantRole")
    class GrantRole {

        @Test
        @DisplayName("role ngoài LEGAL_VERIFIER/ADMIN -> 400, không đụng DB")
        void notManageableRole_returns400() {
            assertAdminError(() -> service.grantRole(adminId, userId, "OWNER"), HttpStatus.BAD_REQUEST);
            assertAdminError(() -> service.grantRole(adminId, userId, "EXECUTOR"), HttpStatus.BAD_REQUEST);
            assertAdminError(() -> service.grantRole(adminId, userId, null), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(userRepository, roleRepository);
        }

        @Test
        @DisplayName("user không tồn tại -> 404")
        void userNotFound_returns404() {
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

            assertAdminError(() -> service.grantRole(adminId, userId, "LEGAL_VERIFIER"), HttpStatus.NOT_FOUND);
            verifyNoRbacSideEffects();
        }

        @Test
        @DisplayName("user chưa ACTIVE -> 409, không thêm role")
        void userNotActive_returns409() {
            user.setStatus(UserStatus.PENDING);
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            assertAdminError(() -> service.grantRole(adminId, userId, "LEGAL_VERIFIER"), HttpStatus.CONFLICT);
            assertThat(user.getRoles()).containsExactly(ownerRole);
            verifyNoRbacSideEffects();
        }

        @Test
        @DisplayName("gán LEGAL_VERIFIER (viết thường) -> thêm role, thu hồi token, ghi audit")
        void grantVerifier_success() {
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
            when(roleRepository.findByCode("LEGAL_VERIFIER")).thenReturn(Optional.of(verifierRole));

            AdminUserResponse res = service.grantRole(adminId, userId, " legal_verifier ");

            assertThat(user.getRoles()).contains(verifierRole, ownerRole);
            assertThat(res.getRoles()).containsExactly("LEGAL_VERIFIER", "OWNER"); // đã sort
            verify(tokenService).revokeAllUserTokens(userId, RevokedReason.ADMIN_REVOKE);
            verify(auditLogService).log(eq(AuditAction.RBAC_CHANGE), eq(AuditResult.SUCCESS), eq(adminId),
                    isNull(), eq("User"), eq(userId.toString()), eq("GRANT LEGAL_VERIFIER"));
        }

        @Test
        @DisplayName("user đã có role -> idempotent, không thu hồi token, không audit")
        void alreadyHasRole_noop() {
            user.getRoles().add(verifierRole);
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            AdminUserResponse res = service.grantRole(adminId, userId, "LEGAL_VERIFIER");

            assertThat(res.getRoles()).contains("LEGAL_VERIFIER");
            verify(roleRepository, never()).findByCode(any());
            verifyNoRbacSideEffects();
        }
    }

    @Nested
    @DisplayName("A2 revokeRole")
    class RevokeRole {

        @Test
        @DisplayName("tự gỡ ADMIN của chính mình -> 409, không đụng DB")
        void selfRevokeAdmin_returns409() {
            assertAdminError(() -> service.revokeRole(adminId, adminId, "ADMIN"), HttpStatus.CONFLICT);
            verifyNoInteractions(userRepository);
            verifyNoRbacSideEffects();
        }

        @Test
        @DisplayName("role không cho phép -> 400")
        void notManageableRole_returns400() {
            assertAdminError(() -> service.revokeRole(adminId, userId, "BENEFICIARY"), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("gỡ ADMIN của admin khác -> thành công")
        void revokeOtherAdmin_success() {
            user.getRoles().add(adminRole);
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            AdminUserResponse res = service.revokeRole(adminId, userId, "ADMIN");

            assertThat(res.getRoles()).containsExactly("OWNER");
            verify(tokenService).revokeAllUserTokens(userId, RevokedReason.ADMIN_REVOKE);
            verify(auditLogService).log(eq(AuditAction.RBAC_CHANGE), eq(AuditResult.SUCCESS), eq(adminId),
                    isNull(), eq("User"), eq(userId.toString()), eq("REVOKE ADMIN"));
        }

        @Test
        @DisplayName("user không có role đó -> idempotent")
        void userWithoutRole_noop() {
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            AdminUserResponse res = service.revokeRole(adminId, userId, "LEGAL_VERIFIER");

            assertThat(res.getRoles()).containsExactly("OWNER");
            verifyNoRbacSideEffects();
        }

        @Test
        @DisplayName("user không tồn tại -> 404")
        void userNotFound_returns404() {
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

            assertAdminError(() -> service.revokeRole(adminId, userId, "LEGAL_VERIFIER"), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("A3 listUsers")
    class ListUsers {

        private final ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        @Test
        @DisplayName("chuẩn hóa filter: q -> %q% chữ thường, role viết hoa, status parse enum")
        void normalizesFilters() {
            when(userRepository.searchForAdmin(any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(user)));

            service.listUsers("  AbC ", "legal_verifier", "active", 2, 10);

            verify(userRepository).searchForAdmin(eq("%abc%"), eq("LEGAL_VERIFIER"), eq(UserStatus.ACTIVE),
                    pageableCaptor.capture());
            Pageable p = pageableCaptor.getValue();
            assertThat(p.getPageNumber()).isEqualTo(2);
            assertThat(p.getPageSize()).isEqualTo(10);
            assertThat(p.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("filter trống -> truyền null")
        void blankFilters_passNull() {
            when(userRepository.searchForAdmin(any(), any(), any(), any())).thenReturn(Page.empty());

            service.listUsers(" ", "", null, 0, 20);

            verify(userRepository).searchForAdmin(isNull(), isNull(), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("size > 100 bị cắt còn 100; page âm -> 0; size < 1 -> 1")
        void clampsPaging() {
            when(userRepository.searchForAdmin(any(), any(), any(), any())).thenReturn(Page.empty());

            service.listUsers(null, null, null, -5, 500);
            verify(userRepository).searchForAdmin(any(), any(), any(), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("size = 0 -> 1")
        void sizeZero_becomesOne() {
            when(userRepository.searchForAdmin(any(), any(), any(), any())).thenReturn(Page.empty());

            service.listUsers(null, null, null, 0, 0);
            verify(userRepository).searchForAdmin(any(), any(), any(), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("status không hợp lệ -> 400, không query")
        void invalidStatus_returns400() {
            assertAdminError(() -> service.listUsers(null, null, "DELETED", 0, 20), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("map đúng các field trả về")
        void mapsResponse() {
            Instant created = Instant.parse("2026-10-01T00:00:00Z");
            Instant lastLogin = Instant.parse("2026-10-18T00:00:00Z");
            user.setCreatedAt(created);
            user.setLastLoginAt(lastLogin);
            user.getRoles().add(verifierRole);
            when(userRepository.searchForAdmin(any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(user)));

            AdminUserResponse r = service.listUsers(null, null, null, 0, 20).getContent().get(0);

            assertThat(r.getId()).isEqualTo(userId);
            assertThat(r.getEmail()).isEqualTo("user@example.test");
            assertThat(r.getFullName()).isEqualTo("Test User");
            assertThat(r.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(r.getRoles()).containsExactly("LEGAL_VERIFIER", "OWNER");
            assertThat(r.getCreatedAt()).isEqualTo(created);
            assertThat(r.getLastLoginAt()).isEqualTo(lastLogin);
        }
    }

    @Nested
    @DisplayName("A4 updateStatus")
    class UpdateStatus {

        private UpdateUserStatusRequest req(String status, String reason) {
            UpdateUserStatusRequest r = new UpdateUserStatusRequest();
            r.setStatus(status);
            r.setReason(reason);
            return r;
        }

        @Test
        @DisplayName("khóa user ACTIVE -> LOCKED, thu hồi token, audit có lý do")
        void lockActiveUser_success() {
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            AdminUserResponse res = service.updateStatus(adminId, userId, req("locked", "  spam  "));

            assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
            assertThat(res.getStatus()).isEqualTo(UserStatus.LOCKED);
            verify(tokenService).revokeAllUserTokens(userId, RevokedReason.ADMIN_REVOKE);
            verify(auditLogService).log(eq(AuditAction.RBAC_CHANGE), eq(AuditResult.SUCCESS), eq(adminId),
                    isNull(), eq("User"), eq(userId.toString()), eq("STATUS ACTIVE->LOCKED reason=spam"));
        }

        @Test
        @DisplayName("mở khóa LOCKED -> ACTIVE, reset failedLoginAttempts/lockedUntil, không thu hồi token")
        void unlockUser_resetsCounters() {
            user.setStatus(UserStatus.LOCKED);
            user.setFailedLoginAttempts(5);
            user.setLockedUntil(Instant.now().plusSeconds(600));
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            service.updateStatus(adminId, userId, req("ACTIVE", null));

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getLockedUntil()).isNull();
            verify(tokenService, never()).revokeAllUserTokens(any(), any());
            verify(auditLogService).log(eq(AuditAction.RBAC_CHANGE), eq(AuditResult.SUCCESS), eq(adminId),
                    isNull(), eq("User"), eq(userId.toString()), eq("STATUS LOCKED->ACTIVE"));
        }

        @Test
        @DisplayName("đổi sang PENDING -> 400")
        void targetPending_returns400() {
            assertAdminError(() -> service.updateStatus(adminId, userId, req("PENDING", null)),
                    HttpStatus.BAD_REQUEST);
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("status không hợp lệ -> 400")
        void invalidStatus_returns400() {
            assertAdminError(() -> service.updateStatus(adminId, userId, req("BANNED", null)),
                    HttpStatus.BAD_REQUEST);
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("tự đổi trạng thái của mình -> 409")
        void self_returns409() {
            assertAdminError(() -> service.updateStatus(adminId, adminId, req("LOCKED", null)),
                    HttpStatus.CONFLICT);
            verifyNoInteractions(userRepository);
            verifyNoRbacSideEffects();
        }

        @Test
        @DisplayName("user đang PENDING (chưa xác minh email) -> 409")
        void userPending_returns409() {
            user.setStatus(UserStatus.PENDING);
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            assertAdminError(() -> service.updateStatus(adminId, userId, req("LOCKED", null)),
                    HttpStatus.CONFLICT);
            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
            verifyNoRbacSideEffects();
        }

        @Test
        @DisplayName("trạng thái không đổi -> idempotent, không thu hồi token, không audit")
        void sameStatus_noop() {
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            AdminUserResponse res = service.updateStatus(adminId, userId, req("ACTIVE", null));

            assertThat(res.getStatus()).isEqualTo(UserStatus.ACTIVE);
            verifyNoRbacSideEffects();
        }

        @Test
        @DisplayName("user không tồn tại -> 404")
        void userNotFound_returns404() {
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

            assertAdminError(() -> service.updateStatus(adminId, userId, req("LOCKED", null)),
                    HttpStatus.NOT_FOUND);
        }
    }
}