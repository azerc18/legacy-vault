package com.ltld.app.legacyvault.service.accountservice;

import com.ltld.app.legacyvault.dto.accountdto.AccountDeletionCheckResponse;
import com.ltld.app.legacyvault.dto.accountdto.DeleteAccountRequest;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.MfaBackupCodeRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.mfaservice.MfaService;
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionServiceImpl implements AccountDeletionService {

    static final String DELETED_NAME = "Deleted user";
    static final String DELETED_EMAIL_DOMAIN = "@deleted.invalid";   // .invalid không bao giờ nhận được mail (RFC 2606)
    private static final Set<String> STAFF_ROLES = Set.of("ADMIN", "LEGAL_VERIFIER");
    private static final Set<VaultStatus> OPEN_VAULT_STATUSES = Set.of(VaultStatus.ACTIVE, VaultStatus.UNLOCKED);
    // Giống đăng nhập: sai mật khẩu / mã 2FA 5 lần thì khoá tạm 15 phút
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final VaultRepository vaultRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final DmsConfigRepository dmsConfigRepository;
    private final MfaBackupCodeRepository mfaBackupCodeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MfaService mfaService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public AccountDeletionCheckResponse check(UUID userId) {
        User user = load(userId);
        List<String> blockers = blockers(user);
        return AccountDeletionCheckResponse.builder()
                .deletable(blockers.isEmpty())
                .blockers(blockers)
                .vaultCount(vaultRepository.countByOwnerId(userId))
                .mfaRequired(user.isMfaEnabled())
                .build();
    }

    // noRollbackFor: lần xác nhận sai vẫn giữ bản ghi audit
    @Override
    @Transactional(noRollbackFor = UserProfileException.class)
    public void delete(UUID userId, DeleteAccountRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserProfileException(ErrorCode.NOT_FOUND, "Account not found"));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            fail(user, "temporarily locked");
            throw new UserProfileException("Too many failed attempts. Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        if (user.getLockedUntil() != null) {   // khoá cũ đã hết hạn: đếm lại từ đầu như đăng nhập
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            badSecret(user, "wrong password");
            throw new UserProfileException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }
        if (request.getConfirmEmail() == null
                || !request.getConfirmEmail().trim().equalsIgnoreCase(user.getEmail())) {
            fail(user, "email mismatch");
            throw new UserProfileException("The email you typed does not match your account", HttpStatus.BAD_REQUEST);
        }
        // Kiểm blockers trước 2FA để mã dự phòng không bị dùng mất khi tài khoản còn bị chặn
        List<String> blockers = blockers(user);
        if (!blockers.isEmpty()) {
            fail(user, "blocked " + blockers);
            throw new UserProfileException("Your account cannot be deleted yet: " + String.join(", ", blockers),
                    HttpStatus.CONFLICT);
        }
        if (user.isMfaEnabled() && !mfaService.verifyCode(user, request.getCode())) {
            badSecret(user, "invalid 2fa code");
            throw new UserProfileException("The two-step verification code is incorrect", HttpStatus.BAD_REQUEST);
        }

        String oldAvatar = user.getAvatarPath();
        Instant now = Instant.now();

        // Ẩn danh: email không gửi được thư, giữ unique; mật khẩu ngẫu nhiên không ai biết
        user.setEmail("deleted-" + user.getId() + DELETED_EMAIL_DOMAIN);
        user.setFullName(DELETED_NAME);
        user.setPhoneNumber(null);
        user.setPasswordHash(passwordEncoder.encode(randomSecret()));
        user.setAvatarPath(null);
        user.setAvatarUpdatedAt(now);
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaLastUsedStep(null);
        user.setMfaChallengeHash(null);
        user.setMfaChallengeExpiresAt(null);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(now);
        userRepository.save(user);

        mfaBackupCodeRepository.deleteByUserId(userId);
        tokenService.revokeAllUserTokens(userId, RevokedReason.LOGOUT);

        // Không còn két nhưng DMS vẫn có thể đang chạy -> dừng để không gửi nhắc check-in
        dmsConfigRepository.findByOwnerId(userId)
                .filter(c -> c.getStatus() == DmsStatus.ACTIVE || c.getStatus() == DmsStatus.PENDING_CONFIRMATION)
                .ifPresent(c -> {
                    c.setStatus(DmsStatus.SUSPENDED);
                    dmsConfigRepository.save(c);
                });

        // Gói còn hiệu lực bị huỷ (không hoàn tiền) để không nhắc / gia hạn / thu tiền tiếp
        subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE).forEach(sub -> {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(sub);
        });

        if (oldAvatar != null) {
            afterCommit(() -> fileStorageService.deleteFile(oldAvatar));
        }
        // Không ghi email gốc vào audit (đã ẩn danh)
        auditLogService.log(AuditAction.ACCOUNT_DELETED, AuditResult.SUCCESS, userId, null, "User", userId.toString(), null);
    }

    private List<String> blockers(User user) {
        UUID id = user.getId();
        List<String> out = new ArrayList<>();
        if (user.getRoles().stream().map(Role::getCode).map(c -> c.toUpperCase(Locale.ROOT)).anyMatch(STAFF_ROLES::contains)) {
            out.add("STAFF_ROLE");
        }
        if (vaultRepository.existsByOwnerId(id)) {
            out.add("VAULTS_OWNED");
        }
        if (!executorAssignmentRepository.findLiveByExecutorUserId(id).isEmpty()) {
            out.add("EXECUTOR");
        }
        if (vaultBeneficiaryRepository.existsLiveLinkForUser(id)
                || vaultRepository.existsByBeneficiaryIdAndStatusIn(id, OPEN_VAULT_STATUSES)) {
            out.add("BENEFICIARY");
        }
        return out;
    }

    // Sai mật khẩu / mã 2FA: đếm chung bộ đếm với đăng nhập, đủ 5 lần thì khoá tạm
    private void badSecret(User user, String detail) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
            user.setFailedLoginAttempts(0);
        }
        userRepository.save(user);
        fail(user, detail + ", attempt " + attempts);
    }

    private void fail(User user, String detail) {
        auditLogService.log(AuditAction.ACCOUNT_DELETE_FAILED, AuditResult.FAILURE, user.getId(), null,
                "User", user.getId().toString(), detail);
    }

    private User load(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserProfileException(ErrorCode.NOT_FOUND, "Account not found"));
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void afterCommit(Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("Could not delete avatar file of a deleted account: {}", e.getMessage());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safe.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safe.run();
            }
        });
    }
}
