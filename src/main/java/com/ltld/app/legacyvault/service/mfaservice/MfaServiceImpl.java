package com.ltld.app.legacyvault.service.mfaservice;

import com.ltld.app.legacyvault.dto.mfadto.MfaSetupResponse;
import com.ltld.app.legacyvault.entity.MfaBackupCode;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.MfaBackupCodeRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.utility.OtpHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MfaServiceImpl implements MfaService {

    private static final int BACKUP_CODE_COUNT = 10;
    // Bỏ 0/O, 1/I để người dùng không chép nhầm
    private static final String BACKUP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final MfaBackupCodeRepository backupCodeRepository;
    private final TotpService totpService;
    private final CryptoService cryptoService;
    private final OtpHasher otpHasher;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public MfaSetupResponse setup(UUID userId) {
        User user = load(userId);
        if (user.isMfaEnabled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Two-factor authentication is already enabled");
        }
        String secret = totpService.newSecret();
        user.setMfaSecret(encrypt(secret));
        user.setMfaLastUsedStep(null);
        userRepository.save(user);

        return new MfaSetupResponse(
                secret,
                totpService.otpauthUri(secret, user.getEmail()),
                totpService.qrDataUri(secret, user.getEmail()));
    }

    // noRollbackFor: mã sai vẫn giữ bản ghi audit MFA_VERIFY_FAILED
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public List<String> enable(UUID userId, String code) {
        User user = load(userId);
        if (user.isMfaEnabled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Two-factor authentication is already enabled");
        }
        if (user.getMfaSecret() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Start the setup first");
        }

        OptionalLong step = totpService.verify(decrypt(user.getMfaSecret()), code, null);
        if (step.isEmpty()) {
            auditLogService.failure(AuditAction.MFA_VERIFY_FAILED, user.getId(), user.getEmail(), "enable: wrong code");
            throw new BusinessException(ErrorCode.MFA_INVALID_CODE, "Invalid verification code");
        }

        user.setMfaEnabled(true);
        user.setMfaLastUsedStep(step.getAsLong());
        userRepository.save(user);

        List<String> codes = regenerateBackupCodes(user);
        auditLogService.success(AuditAction.MFA_ENABLED, user.getId(), user.getEmail());
        return codes;
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public void disable(UUID userId, String code) {
        User user = load(userId);
        if (!user.isMfaEnabled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Two-factor authentication is not enabled");
        }
        if (!verifyCode(user, code)) {
            auditLogService.failure(AuditAction.MFA_VERIFY_FAILED, user.getId(), user.getEmail(), "disable: wrong code");
            throw new BusinessException(ErrorCode.MFA_INVALID_CODE, "Invalid verification code");
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaLastUsedStep(null);
        user.setMfaChallengeHash(null);
        user.setMfaChallengeExpiresAt(null);
        userRepository.save(user);
        backupCodeRepository.deleteByUserId(user.getId());

        auditLogService.success(AuditAction.MFA_DISABLED, user.getId(), user.getEmail());
    }

    @Override
    public boolean verifyCode(User user, String code) {
        if (!user.isMfaEnabled() || user.getMfaSecret() == null || code == null) return false;
        String trimmed = code.trim();

        // 1) Mã TOTP 6 số; chặn dùng lại mã cũ qua mfaLastUsedStep
        if (trimmed.matches("\\d{6}")) {
            OptionalLong step = totpService.verify(decrypt(user.getMfaSecret()), trimmed, user.getMfaLastUsedStep());
            if (step.isEmpty()) return false;
            user.setMfaLastUsedStep(step.getAsLong());
            userRepository.save(user);
            return true;
        }

        // 2) Mã dự phòng, mỗi mã dùng 1 lần
        String normalized = normalizeBackupCode(trimmed);
        for (MfaBackupCode backup : backupCodeRepository.findByUserIdAndUsedAtIsNull(user.getId())) {
            if (otpHasher.matches(user.getId(), normalized, backup.getCodeHash())) {
                backup.setUsedAt(Instant.now());
                backupCodeRepository.save(backup);
                auditLogService.success(AuditAction.MFA_BACKUP_CODE_USED, user.getId(), user.getEmail());
                return true;
            }
        }
        return false;
    }

    // ---------- helpers ----------

    private List<String> regenerateBackupCodes(User user) {
        backupCodeRepository.deleteByUserId(user.getId());
        List<String> plain = new ArrayList<>(BACKUP_CODE_COUNT);
        List<MfaBackupCode> entities = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String code = randomBackupCode();
            plain.add(code);
            entities.add(MfaBackupCode.builder()
                    .user(user)
                    .codeHash(otpHasher.hash(user.getId(), normalizeBackupCode(code)))
                    .build());
        }
        backupCodeRepository.saveAll(entities);
        return plain;
    }

    // Dạng XXXX-XXXX
    private static String randomBackupCode() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) sb.append('-');
            sb.append(BACKUP_ALPHABET.charAt(RANDOM.nextInt(BACKUP_ALPHABET.length())));
        }
        return sb.toString();
    }

    // "abcd-efgh " -> "ABCDEFGH"
    private static String normalizeBackupCode(String code) {
        return code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private User load(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Account not found"));
    }

    private String encrypt(String plain) {
        try {
            return cryptoService.encrypt(plain);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot encrypt MFA secret", e);
        }
    }

    private String decrypt(String cipher) {
        try {
            return cryptoService.decrypt(cipher);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot decrypt MFA secret", e);
        }
    }
}