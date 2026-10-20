package com.ltld.app.legacyvault.service.userservice;

import com.ltld.app.legacyvault.dto.userdto.ChangePasswordRequest;
import com.ltld.app.legacyvault.dto.userdto.UpdateProfileRequest;
import com.ltld.app.legacyvault.dto.userdto.UserProfileResponse;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    private static final String OLD_HASH = "$2a$old-hash";

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private AuditLogService auditLogService;
    @Mock private FileStorageService fileStorageService;
    @InjectMocks private UserProfileServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    private User user() {
        Set<Role> roles = new HashSet<>();
        roles.add(Role.builder().code("OWNER").name("Owner").build());
        roles.add(Role.builder().code("BENEFICIARY").name("Beneficiary").build());
        return User.builder()
                .id(userId)
                .email("owner@example.com")
                .fullName("Nguyen Van A")
                .phoneNumber("0901234567")
                .passwordHash(OLD_HASH)
                .status(UserStatus.ACTIVE)
                .roles(roles)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .lastLoginAt(Instant.parse("2026-10-01T09:00:00Z"))
                .build();
    }

    private static UpdateProfileRequest updateRequest(String fullName, String phone) {
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setFullName(fullName);
        r.setPhoneNumber(phone);
        return r;
    }

    private static ChangePasswordRequest changeRequest(String current, String next) {
        ChangePasswordRequest r = new ChangePasswordRequest();
        r.setCurrentPassword(current);
        r.setNewPassword(next);
        r.setPasswordConfirm(next);
        return r;
    }

    // ---------- getMe ----------

    @Test
    void getMe_existingUser_mapsAllFieldsAndSortsRoles() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));

        UserProfileResponse res = service.getMe(userId);

        assertEquals(userId, res.getId());
        assertEquals("owner@example.com", res.getEmail());
        assertEquals("Nguyen Van A", res.getFullName());
        assertEquals("0901234567", res.getPhoneNumber());
        assertEquals(UserStatus.ACTIVE, res.getStatus());
        assertEquals(List.of("BENEFICIARY", "OWNER"), res.getRoles());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), res.getCreatedAt());
        assertEquals(Instant.parse("2026-10-01T09:00:00Z"), res.getLastLoginAt());
    }

    @Test
    void getMe_unknownUser_throws404() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        UserProfileException ex = assertThrows(UserProfileException.class, () -> service.getMe(userId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Không tìm thấy tài khoản", ex.getMessage());
    }

    // ---------- updateMe ----------

    @Test
    void updateMe_trimsNameAndPhone_savesAndAudits() {
        User u = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        UserProfileResponse res = service.updateMe(userId, updateRequest("  Tran Thi B  ", " 0912 345 678 "));

        assertEquals("Tran Thi B", u.getFullName());
        assertEquals("0912 345 678", u.getPhoneNumber());
        assertEquals("Tran Thi B", res.getFullName());
        verify(userRepository).save(u);
        verify(auditLogService).success(AuditAction.PROFILE_UPDATED, userId, "owner@example.com");
    }

    @Test
    void updateMe_blankPhone_clearsPhoneNumber() {
        User u = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        UserProfileResponse res = service.updateMe(userId, updateRequest("Nguyen Van A", "   "));

        assertNull(u.getPhoneNumber());
        assertNull(res.getPhoneNumber());
    }

    @Test
    void updateMe_nullPhone_clearsPhoneNumber() {
        User u = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        service.updateMe(userId, updateRequest("Nguyen Van A", null));

        assertNull(u.getPhoneNumber());
    }

    @Test
    void updateMe_doesNotTouchEmailOrPassword() {
        User u = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        service.updateMe(userId, updateRequest("New Name", "0900000000"));

        assertEquals("owner@example.com", u.getEmail());
        assertEquals(OLD_HASH, u.getPasswordHash());
    }

    @Test
    void updateMe_unknownUser_throws404AndSavesNothing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        UserProfileException ex = assertThrows(UserProfileException.class,
                () -> service.updateMe(userId, updateRequest("Name", null)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(userRepository, never()).save(any());
        verify(auditLogService, never()).success(any(), any(), any());
    }

    // ---------- changePassword ----------

    @Test
    void changePassword_success_encodesSavesRevokesTokensAndAudits() {
        User u = user();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("old-pass", OLD_HASH)).thenReturn(true);
        when(passwordEncoder.matches("new-pass", OLD_HASH)).thenReturn(false);
        when(passwordEncoder.encode("new-pass")).thenReturn("$2a$new-hash");

        service.changePassword(userId, changeRequest("old-pass", "new-pass"));

        assertEquals("$2a$new-hash", u.getPasswordHash());
        verify(userRepository).save(u);
        verify(tokenService).revokeAllUserTokens(userId, RevokedReason.PASSWORD_CHANGED);
        verify(auditLogService).success(AuditAction.PASSWORD_CHANGED, userId, "owner@example.com");
    }

    @Test
    void changePassword_wrongCurrentPassword_throws400AndAuditsFailure() {
        User u = user();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrong", OLD_HASH)).thenReturn(false);

        UserProfileException ex = assertThrows(UserProfileException.class,
                () -> service.changePassword(userId, changeRequest("wrong", "new-pass")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Mật khẩu hiện tại không đúng", ex.getMessage());
        assertEquals(OLD_HASH, u.getPasswordHash());
        verify(auditLogService).failure(AuditAction.PASSWORD_CHANGE_FAILED, userId, "owner@example.com",
                "wrong current password");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(tokenService, never()).revokeAllUserTokens(any(), any());
    }

    @Test
    void changePassword_newSameAsCurrent_throws400AndKeepsTokens() {
        User u = user();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("same-pass", OLD_HASH)).thenReturn(true);

        UserProfileException ex = assertThrows(UserProfileException.class,
                () -> service.changePassword(userId, changeRequest("same-pass", "same-pass")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Mật khẩu mới phải khác mật khẩu hiện tại", ex.getMessage());
        verify(userRepository, never()).save(any());
        verify(tokenService, never()).revokeAllUserTokens(any(), any());
        verify(auditLogService, never()).success(any(), any(), any());
    }

    @Test
    void changePassword_unknownUser_throws404() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

        UserProfileException ex = assertThrows(UserProfileException.class,
                () -> service.changePassword(userId, changeRequest("old-pass", "new-pass")));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(tokenService, never()).revokeAllUserTokens(any(), any());
    }

    @Test
    void changePassword_usesPessimisticLockQuery_notPlainFindById() {
        User u = user();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("old-pass", OLD_HASH)).thenReturn(true);
        when(passwordEncoder.matches("new-pass", OLD_HASH)).thenReturn(false);
        when(passwordEncoder.encode("new-pass")).thenReturn("$2a$new-hash");

        service.changePassword(userId, changeRequest("old-pass", "new-pass"));

        verify(userRepository, never()).findById(any());
    }
}
