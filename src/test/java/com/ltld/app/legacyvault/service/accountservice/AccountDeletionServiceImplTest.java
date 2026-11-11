package com.ltld.app.legacyvault.service.accountservice;

import com.ltld.app.legacyvault.dto.accountdto.DeleteAccountRequest;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.mfaservice.MfaService;
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private MfaBackupCodeRepository mfaBackupCodeRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private MfaService mfaService;
    @Mock private TokenService tokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogService auditLogService;
    @Mock private FileStorageService fileStorageService;
    @Mock private com.ltld.app.legacyvault.repository.OwnerIdentityRepository ownerIdentityRepository;
    @Mock private com.ltld.app.legacyvault.repository.PhoneOtpChallengeRepository phoneOtpChallengeRepository;
    @InjectMocks private AccountDeletionServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).email("a@example.com").fullName("A").phoneNumber("0901")
                .passwordHash("HASH").status(UserStatus.ACTIVE).avatarPath("avatars/a.png")
                .roles(new HashSet<>(Set.of(role("OWNER")))).build();
        lenient().when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        lenient().when(passwordEncoder.matches("pw", "HASH")).thenReturn(true);
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("NEW-HASH");
        lenient().when(executorAssignmentRepository.findLiveByExecutorUserId(any())).thenReturn(List.of());
        lenient().when(dmsConfigRepository.findByOwnerId(any())).thenReturn(Optional.empty());
        lenient().when(subscriptionRepository.findByUserIdAndStatus(any(), any())).thenReturn(List.of());
    }

    private static Role role(String code) {
        Role r = new Role();
        r.setCode(code);
        return r;
    }

    private static DeleteAccountRequest req(String password, String email, String code) {
        DeleteAccountRequest r = new DeleteAccountRequest();
        r.setPassword(password);
        r.setConfirmEmail(email);
        r.setCode(code);
        return r;
    }

    private void assertRejected(DeleteAccountRequest r, HttpStatus status) {
        assertThatThrownBy(() -> service.delete(user.getId(), r))
                .isInstanceOf(UserProfileException.class)
                .extracting(e -> ((UserProfileException) e).getStatus()).isEqualTo(status);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(tokenService, never()).revokeAllUserTokens(any(), any());
        verify(auditLogService).log(eq(AuditAction.ACCOUNT_DELETE_FAILED), eq(AuditResult.FAILURE), eq(user.getId()), isNull(),
                eq("User"), anyString(), anyString());
    }

    @Test
    void wrongPassword_isRejected() {
        assertRejected(req("bad", "a@example.com", null), HttpStatus.BAD_REQUEST);
    }

    @Test
    void emailMismatch_isRejected() {
        assertRejected(req("pw", "someone@example.com", null), HttpStatus.BAD_REQUEST);
    }

    @Test
    void mfaEnabled_wrongCode_isRejected() {
        user.setMfaEnabled(true);
        when(mfaService.verifyCode(user, "000000")).thenReturn(false);
        assertRejected(req("pw", "a@example.com", "000000"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void staffAccount_isBlocked() {
        user.getRoles().add(role("ADMIN"));
        assertRejected(req("pw", "a@example.com", null), HttpStatus.CONFLICT);
    }

    @Test
    void ownerWithVaults_isBlocked() {
        when(vaultRepository.existsByOwnerId(user.getId())).thenReturn(true);
        assertRejected(req("pw", "a@example.com", null), HttpStatus.CONFLICT);
    }

    @Test
    void success_anonymizesAndRevokes_withoutLoggingOriginalEmail() {
        service.delete(user.getId(), req("pw", " A@Example.com ", null));

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getEmail()).isEqualTo("deleted-" + user.getId() + "@deleted.invalid");
        assertThat(user.getFullName()).isEqualTo("Deleted user");
        assertThat(user.getPhoneNumber()).isNull();
        assertThat(user.getAvatarPath()).isNull();
        assertThat(user.getPasswordHash()).isEqualTo("NEW-HASH");
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.isMfaEnabled()).isFalse();
        verify(mfaBackupCodeRepository).deleteByUserId(user.getId());
        verify(tokenService).revokeAllUserTokens(eq(user.getId()), any());
        verify(fileStorageService).deleteFile("avatars/a.png");             // không có transaction -> chạy ngay
        verify(auditLogService).log(eq(AuditAction.ACCOUNT_DELETED), eq(AuditResult.SUCCESS), eq(user.getId()), isNull(),
                eq("User"), eq(user.getId().toString()), isNull());
    }

    @Test
    void mfaEnabled_validCode_succeeds() {
        user.setMfaEnabled(true);
        when(mfaService.verifyCode(user, "123456")).thenReturn(true);

        service.delete(user.getId(), req("pw", "a@example.com", "123456"));
        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
    }

    @Test
    void check_reportsBlockersAndMfa() {
        user.setMfaEnabled(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(vaultRepository.existsByOwnerId(user.getId())).thenReturn(true);
        when(vaultRepository.countByOwnerId(user.getId())).thenReturn(2L);

        var res = service.check(user.getId());
        assertThat(res.isDeletable()).isFalse();
        assertThat(res.getBlockers()).containsExactly("VAULTS_OWNED");
        assertThat(res.getVaultCount()).isEqualTo(2);
        assertThat(res.isMfaRequired()).isTrue();
    }

    @Test
    void wrongPassword_countsAttempt_andLocksAfterFive() {
        user.setFailedLoginAttempts(AccountDeletionServiceImpl.MAX_FAILED_ATTEMPTS - 1);
        assertRejected(req("bad", "a@example.com", null), HttpStatus.BAD_REQUEST);
        assertThat(user.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void lockedAccount_isRejectedBeforeCheckingPassword() {
        user.setLockedUntil(Instant.now().plusSeconds(600));
        assertRejected(req("pw", "a@example.com", null), HttpStatus.TOO_MANY_REQUESTS);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void blockedAccount_doesNotConsume2faCode() {
        user.setMfaEnabled(true);
        when(vaultRepository.existsByOwnerId(user.getId())).thenReturn(true);
        assertRejected(req("pw", "a@example.com", "123456"), HttpStatus.CONFLICT);
        verify(mfaService, never()).verifyCode(any(), any());
    }

    @Test
    void beneficiaryOfOpenVault_isBlocked() {
        when(vaultBeneficiaryRepository.existsLiveLinkForUser(user.getId())).thenReturn(true);
        assertRejected(req("pw", "a@example.com", null), HttpStatus.CONFLICT);
    }

    @Test
    void success_cancelsActiveSubscriptions() {
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE)).thenReturn(List.of(sub));

        service.delete(user.getId(), req("pw", "a@example.com", null));
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(subscriptionRepository).save(sub);
    }
}
