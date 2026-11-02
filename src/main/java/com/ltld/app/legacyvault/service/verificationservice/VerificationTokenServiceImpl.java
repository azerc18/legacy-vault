package com.ltld.app.legacyvault.service.verificationservice;

import com.ltld.app.legacyvault.dto.forgotpassworddto.ForgotPasswordRequest;
import com.ltld.app.legacyvault.dto.otpdto.SendOtpRequest;
import com.ltld.app.legacyvault.dto.otpdto.VerifyOtpRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.entity.VerificationToken;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.TokenType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.*;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.repository.VerificationTokenRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VerificationTokenServiceImpl implements VerificationTokenService {
    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final OtpGenerator otpGenerator;
    private final EmailSender emailSender;
    private final AuditLogService auditLogService;
    private final VaultRepository vaultRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final OtpHasher otpHasher;

    private static final int MAX_ATTEMPTS = 5;
    private static final int OTP_TTL = 300;
    private static final Duration OTP_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration OTP_WINDOW = Duration.ofHours(1);
    private static final int MAX_OTP_PER_WINDOW = 5;

    @Override
    @Transactional
    public void sendOtp(SendOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        userRepository.findByEmail(email).ifPresent(u -> issueOtp(u, TokenType.EMAIL_VERIFICATION));
    }

    @Override
    @Transactional
    public void issueOtp(User user, TokenType type) {
        if (type == TokenType.EMAIL_VERIFICATION && user.getStatus() == UserStatus.ACTIVE) return;

        if(user.getStatus() == UserStatus.LOCKED){
            auditLogService.failure(AuditAction.ACCOUNT_LOCKED, user.getId(), user.getEmail(), "account locked");
            throw new LockedAccountException();
        }

        Instant now = Instant.now();

        // Cooldown 60s
        Optional<VerificationToken> last = tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, type);
        if (last.isPresent() && last.get().getCreatedAt().plus(OTP_COOLDOWN).isAfter(now)) {
            auditLogService.failure(AuditAction.OTP_RATE_LIMITED, user.getId(), user.getEmail(), "cooldown");
            throw new TooManyOtpRequestsException(
                    Duration.between(now, last.get().getCreatedAt().plus(OTP_COOLDOWN)).toSeconds());
        }

        // Tối đa 5 lần / giờ
        long sent = tokenRepository.countByUserAndTypeAndCreatedAtAfter(user, type, now.minus(OTP_WINDOW));
        if (sent >= MAX_OTP_PER_WINDOW) {
            auditLogService.failure(AuditAction.OTP_RATE_LIMITED, user.getId(), user.getEmail(), "hourly limit");
            throw new TooManyOtpRequestsException(OTP_WINDOW.toSeconds());
        }

        tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, type)
                .ifPresent(old -> { old.setUsed(true); tokenRepository.save(old); });

        String otp = otpGenerator.generate();
        tokenRepository.save(VerificationToken.builder()
                .otpCode(otpHasher.hash(user.getId(), otp))
                .expiresAt(Instant.now().plusSeconds(OTP_TTL))
                .isUsed(false)
                .attemptCount(0)
                .createdAt(Instant.now())
                .user(user)
                .type(type)
                .build());

        auditLogService.success(AuditAction.OTP_SENT, user.getId(), user.getEmail());
        String email = user.getEmail();
        runAfterCommit(() -> {
            if (type == TokenType.PASSWORD_RESET) {
                emailSender.sendPasswordResetEmail(email, otp);
            } else {
                emailSender.sendEmail(email, otp);
            }
        });
    }

    @Override
    @Transactional
    public void sendPasswordResetOtp(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            auditLogService.failure(AuditAction.PASSWORD_RESET_REQUEST,
                    user == null ? null : user.getId(), email, "user not found or not active");
            return;
        }
        try {
            issueOtp(user, TokenType.PASSWORD_RESET);
        } catch (TooManyOtpRequestsException e) {
            return;
        }
        auditLogService.success(AuditAction.PASSWORD_RESET_REQUEST, user.getId(), email);
    }

    @Override
    @Transactional(noRollbackFor = {InvalidOtpException.class,
            TooManyAttemptsException.class, ExpiredOtpException.class})
    public void consumeOtp(User user, TokenType type, String otp, AuditAction failAction) {
        String email = user.getEmail();
        VerificationToken token = tokenRepository
                .findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, type).orElse(null);
        if (token == null) {
            auditLogService.failure(failAction, user.getId(), email, "no active otp");
            throw new OtpNotFoundException("OTP not found");
        }

        if (token.getAttemptCount() >= MAX_ATTEMPTS) {
            auditLogService.failure(failAction, user.getId(), email, "too many attempts");
            throw new TooManyAttemptsException("Too many failed attempts");
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            auditLogService.failure(failAction, user.getId(), email, "expired");
            throw new ExpiredOtpException("OTP expired for user");
        }

        if (!otpHasher.matches(user.getId(), otp, token.getOtpCode())) {
            token.setAttemptCount(token.getAttemptCount() + 1);
            tokenRepository.saveAndFlush(token);
            auditLogService.failure(failAction, user.getId(), email,
                    "wrong otp, attempt " + token.getAttemptCount());
            throw new InvalidOtpException("Wrong OTP for user");
        }

        token.setUsed(true);
        token.setUsedAt(Instant.now());
        tokenRepository.saveAndFlush(token);
    }

    @Override
    @Transactional(noRollbackFor = {InvalidOtpException.class,
            TooManyAttemptsException.class, ExpiredOtpException.class})
    public void verifyEmail(VerifyOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            auditLogService.failure(AuditAction.OTP_FAILED,null, email, "user not found");
            throw new OtpNotFoundException("OTP not found");
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            auditLogService.failure(AuditAction.OTP_FAILED, user.getId(), email, "account locked");
            throw new LockedAccountException();
        }

        consumeOtp(user, TokenType.EMAIL_VERIFICATION, request.getOtp(), AuditAction.OTP_FAILED);

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        auditLogService.success(AuditAction.EMAIL_VERIFIED, user.getId(), email);

        linkPendingBeneficiaryVaults(user);
    }

    // FR-02: liên kết các Vault đã mời email này làm người thụ hưởng khi chưa có tài khoản.
    // Dùng email đã chuẩn hóa (lowercase) của user, khớp với beneficiary_email đã lưu, không dùng email từ request.
    private void linkPendingBeneficiaryVaults(User user) {
        String email = user.getEmail();
        List<Vault> pendingVaults = vaultRepository.findByBeneficiaryEmailAndBeneficiaryIsNull(email);
        for (Vault vault : pendingVaults) {
            vault.setBeneficiary(user);
            vault.setBeneficiaryEmail(null);
            auditLogService.log(AuditAction.BENEFICIARY_LINKED, AuditResult.SUCCESS, user.getId(), email,
                    vault.getId(), "Vault", vault.getId().toString(), null);
        }
        vaultRepository.saveAll(pendingVaults);
        // N2a: cập nhật bảng mới: gắn tài khoản và chuyển sang INVITED
        List<VaultBeneficiary> pendingRows = vaultBeneficiaryRepository.findPendingByEmail(email);
        for (VaultBeneficiary row : pendingRows) {
            row.setUser(user);
            row.setStatus(BeneficiaryInviteStatus.INVITED);
        }
        vaultBeneficiaryRepository.saveAll(pendingRows);
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
