package com.ltld.app.legacyvault.service.userservice;

import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.dto.userdto.SetPasswordRequest;
import com.ltld.app.legacyvault.dto.userdto.ChangePasswordRequest;
import com.ltld.app.legacyvault.dto.userdto.UpdateProfileRequest;
import com.ltld.app.legacyvault.dto.userdto.UserProfileResponse;
import com.ltld.app.legacyvault.service.verificationservice.VerificationTokenService;
import com.ltld.app.legacyvault.service.mfaservice.MfaService;
import com.ltld.app.legacyvault.enums.TokenType;
import com.ltld.app.legacyvault.exception.InvalidOtpException;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.enums.AppLanguage;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.dto.userdto.AvatarFile;
import com.ltld.app.legacyvault.exception.StorageUnavailableException;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private CryptoService cryptoService;
    @Mock private VerificationTokenService verificationTokenService;
    @Mock private MfaService mfaService;
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
        assertEquals("Account not found", ex.getMessage());
    }

    // ---------- updateMe ----------

    @Test
    void updateMe_trimsNameAndPhone_savesAndAudits() {
        User u = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        UserProfileResponse res = service.updateMe(userId, updateRequest("  Tran Thi B  ", " 0912 345 678 "));

        assertEquals("Tran Thi B", u.getFullName());
        // Số mới được chuẩn hoá về E.164
        assertEquals("+84912345678", u.getPhoneNumber());
        assertEquals("Tran Thi B", res.getFullName());
        verify(userRepository).save(u);
        verify(auditLogService).success(AuditAction.PROFILE_UPDATED, userId, "owner@example.com");
    }

    @Test
    void updateMe_invalidVietnamesePhone_throwsPhoneInvalid_andSavesNothing() {
        User u = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        UserProfileException ex = assertThrows(UserProfileException.class,
                () -> service.updateMe(userId, updateRequest("Nguyen Van A", "01681234567")));

        assertEquals(com.ltld.app.legacyvault.exception.ErrorCode.PHONE_INVALID, ex.getErrorCode());
        assertEquals("0901234567", u.getPhoneNumber());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateMe_unchangedLegacyPhone_isKeptAsIs_soNameCanStillBeEdited() {
        User u = user();
        u.setPhoneNumber("12345678");   // số cũ không đúng chuẩn, lưu trước khi siết quy tắc
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        service.updateMe(userId, updateRequest("Nguyen Van B", "12345678"));

        assertEquals("12345678", u.getPhoneNumber());
        assertEquals("Nguyen Van B", u.getFullName());
    }

    @Test
    void updateMe_samePhoneDifferentFormat_keepsStoredValueAndVerification() {
        User u = user();
        Instant verifiedAt = Instant.parse("2026-11-01T00:00:00Z");
        u.setPhoneNumber("+84901234567");
        u.setPhoneVerifiedAt(verifiedAt);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        service.updateMe(userId, updateRequest("Nguyen Van A", "0901 234 567"));

        assertEquals("+84901234567", u.getPhoneNumber());
        assertEquals(verifiedAt, u.getPhoneVerifiedAt());
    }

    @Test
    void updateMe_newPhone_resetsPhoneVerification() {
        User u = user();
        u.setPhoneVerifiedAt(Instant.parse("2026-11-01T00:00:00Z"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        service.updateMe(userId, updateRequest("Nguyen Van A", "0912345678"));

        assertEquals("+84912345678", u.getPhoneNumber());
        assertNull(u.getPhoneVerifiedAt());
    }

    @Test
    void updateMe_kycVerified_changingNameOrPhone_throwsProfileLocked() {
        User u = user();
        u.setKycLevel(com.ltld.app.legacyvault.enums.KycLevel.BASIC);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        UserProfileException nameEx = assertThrows(UserProfileException.class,
                () -> service.updateMe(userId, updateRequest("Someone Else", "0901234567")));
        UserProfileException phoneEx = assertThrows(UserProfileException.class,
                () -> service.updateMe(userId, updateRequest("Nguyen Van A", "0912345678")));

        assertEquals(com.ltld.app.legacyvault.exception.ErrorCode.PROFILE_LOCKED_BY_KYC, nameEx.getErrorCode());
        assertEquals(com.ltld.app.legacyvault.exception.ErrorCode.PROFILE_LOCKED_BY_KYC, phoneEx.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateMe_kycVerified_languageOnly_isAllowed() {
        User u = user();
        u.setKycLevel(com.ltld.app.legacyvault.enums.KycLevel.BASIC);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        UpdateProfileRequest req = updateRequest("Nguyen Van A", "0901234567");
        req.setPreferredLanguage("en");

        service.updateMe(userId, req);

        verify(userRepository).save(u);
    }

    @Test
    void updateMe_preferredLanguage_isSavedAndReturned() {
        User u = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        UpdateProfileRequest req = updateRequest("Nguyen Van A", "0901234567");
        req.setPreferredLanguage("en");

        UserProfileResponse res = service.updateMe(userId, req);

        assertEquals(AppLanguage.EN, u.getPreferredLanguage());
        assertEquals("en", res.getPreferredLanguage());
    }

    @Test
    void updateMe_withoutLanguage_keepsCurrentLanguage() {
        User u = user();
        u.setPreferredLanguage(AppLanguage.EN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        UserProfileResponse res = service.updateMe(userId, updateRequest("Nguyen Van A", "0901234567"));

        assertEquals(AppLanguage.EN, u.getPreferredLanguage());
        assertEquals("en", res.getPreferredLanguage());
    }

    @Test
    void getMe_defaultLanguageIsVietnamese() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));

        assertEquals("vi", service.getMe(userId).getPreferredLanguage());
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
        assertEquals("Current password is incorrect", ex.getMessage());
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
        assertEquals("The new password must be different from the current password", ex.getMessage());
        verify(userRepository, never()).save(any());
        verify(tokenService, never()).revokeAllUserTokens(any(), any());
        verify(auditLogService, never()).success(any(), any(), any());
    }

    @Test
    void changePassword_googleAccount_marksPasswordSet() {
        User u = user();
        u.setPasswordSet(false);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("old-pass", OLD_HASH)).thenReturn(true);
        when(passwordEncoder.encode("new-pass")).thenReturn("$2a$new-hash");

        service.changePassword(userId, changeRequest("old-pass", "new-pass"));

        assertTrue(u.isPasswordSet());
    }

    // ---------- Cài đặt mật khẩu cho tài khoản Google ----------

    private static SetPasswordRequest setRequest(String pw) {
        SetPasswordRequest r = new SetPasswordRequest();
        r.setNewPassword(pw);
        r.setPasswordConfirm(pw);
        r.setOtp("123456");
        return r;
    }

    @Test
    void sendSetPasswordOtp_googleAccount_issuesPasswordResetOtp() {
        User u = user();
        u.setPasswordSet(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        service.sendSetPasswordOtp(userId);

        verify(verificationTokenService).issueOtp(u, TokenType.PASSWORD_RESET);
    }

    @Test
    void sendSetPasswordOtp_passwordAlreadySet_throws409AndSendsNothing() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));

        UserProfileException ex = assertThrows(UserProfileException.class, () -> service.sendSetPasswordOtp(userId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(verificationTokenService, never()).issueOtp(any(), any());
    }

    @Test
    void setPassword_wrongOtp_propagates_andChangesNothing() {
        User u = user();
        u.setPasswordSet(false);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(u));
        doThrow(new InvalidOtpException("Wrong OTP for user")).when(verificationTokenService)
                .consumeOtp(u, TokenType.PASSWORD_RESET, "123456", AuditAction.PASSWORD_CHANGE_FAILED);

        assertThrows(InvalidOtpException.class, () -> service.setPassword(userId, setRequest("new-pass")));

        assertEquals(OLD_HASH, u.getPasswordHash());
        assertFalse(u.isPasswordSet());
        verify(userRepository, never()).save(any());
    }

    @Test
    void setPassword_mfaEnabled_wrongCode_throws400AndChangesNothing() {
        User u = user();
        u.setPasswordSet(false);
        u.setMfaEnabled(true);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(u));
        when(mfaService.verifyCode(u, "000000")).thenReturn(false);
        SetPasswordRequest req = setRequest("new-pass");
        req.setCode("000000");

        UserProfileException ex = assertThrows(UserProfileException.class, () -> service.setPassword(userId, req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(OLD_HASH, u.getPasswordHash());
        verify(userRepository, never()).save(any());
        verify(auditLogService).failure(AuditAction.PASSWORD_CHANGE_FAILED, userId, "owner@example.com",
                "set password: invalid 2fa code");
    }

    @Test
    void setPassword_mfaEnabled_correctCode_setsPassword() {
        User u = user();
        u.setPasswordSet(false);
        u.setMfaEnabled(true);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(u));
        when(mfaService.verifyCode(u, "246810")).thenReturn(true);
        when(passwordEncoder.encode("new-pass")).thenReturn("$2a$new-hash");
        SetPasswordRequest req = setRequest("new-pass");
        req.setCode("246810");

        service.setPassword(userId, req);

        assertEquals("$2a$new-hash", u.getPasswordHash());
        assertTrue(u.isPasswordSet());
    }

    @Test
    void setPassword_googleAccount_setsHashWithoutCurrentPassword_keepsSessions() {
        User u = user();
        u.setPasswordSet(false);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(u));
        when(passwordEncoder.encode("new-pass")).thenReturn("$2a$new-hash");

        UserProfileResponse res = service.setPassword(userId, setRequest("new-pass"));

        verify(verificationTokenService).consumeOtp(u, TokenType.PASSWORD_RESET, "123456", AuditAction.PASSWORD_CHANGE_FAILED);
        verify(mfaService, never()).verifyCode(any(), any());   // chưa bật 2FA
        assertEquals("$2a$new-hash", u.getPasswordHash());
        assertTrue(u.isPasswordSet());
        assertTrue(res.isPasswordSet());
        verify(userRepository).save(u);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(tokenService, never()).revokeAllUserTokens(any(), any());
        verify(auditLogService).log(eq(AuditAction.PASSWORD_CHANGED), eq(AuditResult.SUCCESS), eq(userId),
                eq("owner@example.com"), isNull(), isNull(), isNull(), anyString());
    }

    @Test
    void setPassword_passwordAlreadySet_throws409AndChangesNothing() {
        User u = user();   // passwordSet mặc định true
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(u));

        UserProfileException ex = assertThrows(UserProfileException.class,
                () -> service.setPassword(userId, setRequest("new-pass")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(OLD_HASH, u.getPasswordHash());
        verify(verificationTokenService, never()).consumeOtp(any(), any(), any(), any());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void getMe_mapsPasswordSet() {
        User u = user();
        u.setPasswordSet(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        assertFalse(service.getMe(userId).isPasswordSet());
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

    // ---------------- Avatar (H1) ----------------

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    private static MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "a.png", "image/png", PNG);
    }

    @Test
    void updateAvatar_storesEncryptedBytes() throws Exception {
        User u = user();
        byte[] cipher = {7, 7, 7};
        MockMultipartFile file = pngFile();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        when(cryptoService.encryptBytes(PNG)).thenReturn(cipher);
        when(fileStorageService.storeFile(file, cipher)).thenReturn("new-key.png");

        service.updateAvatar(userId, file);

        verify(fileStorageService).storeFile(eq(file), eq(cipher));
        assertEquals("new-key.png", u.getAvatarPath());
    }

    @Test
    void updateAvatar_storageUnavailable_propagates503AndSavesNothing() throws Exception {
        MockMultipartFile file = pngFile();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        when(cryptoService.encryptBytes(PNG)).thenReturn(new byte[]{1});
        when(fileStorageService.storeFile(any(), any()))
                .thenThrow(new StorageUnavailableException("File storage is unavailable.", null));

        assertThrows(StorageUnavailableException.class, () -> service.updateAvatar(userId, file));
        verify(userRepository, never()).save(any());
    }

    @Test
    void getAvatar_decryptsStoredBytes() throws Exception {
        User u = user();
        u.setAvatarPath("key.png");
        byte[] cipher = {9, 9};
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        when(fileStorageService.readFile("key.png")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn(PNG);

        AvatarFile avatar = service.getAvatar(userId);

        assertArrayEquals(PNG, avatar.content());
        assertEquals("image/png", avatar.contentType());
    }

    @Test
    void getAvatar_legacyPlaintext_fallsBackToStoredBytes() throws Exception {
        User u = user();
        u.setAvatarPath("/old/uploads/key.png");
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        when(fileStorageService.readFile("/old/uploads/key.png")).thenReturn(PNG);
        when(cryptoService.decryptBytes(PNG)).thenThrow(new GeneralSecurityException("not ciphertext"));

        assertArrayEquals(PNG, service.getAvatar(userId).content());
    }
}
