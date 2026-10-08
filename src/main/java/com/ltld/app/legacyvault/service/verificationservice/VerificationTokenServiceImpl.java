package com.ltld.app.legacyvault.service.verificationservice;

import com.ltld.app.legacyvault.dto.otpdto.SendOtpRequest;
import com.ltld.app.legacyvault.dto.otpdto.VerifyOtpRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.VerificationToken;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.*;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationTokenRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VerificationTokenServiceImpl implements VerificationTokenService {
    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final OtpGenerator otpGenerator;
    private final EmailSender emailSender;
    private final AuditLogService auditLogService;

    private static final int MAX_ATTEMPTS = 5;
    private static final int OTP_TTL = 300;
    private static final Duration OTP_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration OTP_WINDOW = Duration.ofHours(1);
    private static final int MAX_OTP_PER_WINDOW = 5;

    @Override
    @Transactional
    public void sendOtp(SendOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        userRepository.findByEmail(email).ifPresent(this::issueOtp);
    }

    @Override
    @Transactional
    public void issueOtp(User user) {
        if (user.getStatus() == UserStatus.ACTIVE) return;

        Instant now = Instant.now();

        // Cooldown 60s
        Optional<VerificationToken> last = tokenRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user);
        if (last.isPresent() && last.get().getCreatedAt().plus(OTP_COOLDOWN).isAfter(now)) {
            auditLogService.failure(AuditAction.OTP_RATE_LIMITED, user.getId(), user.getEmail(), "cooldown");
            throw new TooManyOtpRequestsException(
                    Duration.between(now, last.get().getCreatedAt().plus(OTP_COOLDOWN)).toSeconds());
        }

        // Tối đa 5 lần / giờ
        long sent = tokenRepository.countByUserAndCreatedAtAfter(user, now.minus(OTP_WINDOW));
        if (sent >= MAX_OTP_PER_WINDOW) {
            auditLogService.failure(AuditAction.OTP_RATE_LIMITED, user.getId(), user.getEmail(), "hourly limit");
            throw new TooManyOtpRequestsException(OTP_WINDOW.toSeconds());
        }

        tokenRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user)
                .ifPresent(old -> { old.setUsed(true); tokenRepository.save(old); });

        String otp = otpGenerator.generate();
        tokenRepository.save(VerificationToken.builder()
                .otpCode(otp)
                .expiresAt(Instant.now().plusSeconds(OTP_TTL))
                .isUsed(false)
                .attemptCount(0)
                .createdAt(Instant.now())
                .user(user)
                .build());

        emailSender.sendEmail(user.getEmail(), otp);
        auditLogService.success(AuditAction.OTP_SENT, user.getId(), user.getEmail());
    }

    @Override
    @Transactional(noRollbackFor = {InvalidOtpException.class,
            TooManyAttemptsException.class, ExpiredOtpException.class})
    public void verifyEmail(VerifyOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            auditLogService.failure(AuditAction.OTP_FAILED, null, email, "user not found");
            throw new OtpNotFoundException("User not found");
        }


        VerificationToken token = tokenRepository
                .findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user).orElse(null);
        if (token == null) {
            auditLogService.failure(AuditAction.OTP_FAILED, user.getId(), email, "no active otp");
            throw new OtpNotFoundException("OTP not found");
        }

        if (token.getAttemptCount() >= MAX_ATTEMPTS) {
            auditLogService.failure(AuditAction.OTP_FAILED, user.getId(), email, "too many attempts");
            throw new TooManyAttemptsException("Too many failed attempts");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            auditLogService.failure(AuditAction.OTP_FAILED, user.getId(), email, "expired");
            throw new ExpiredOtpException("OTP expired for user");
        }
        if (!token.getOtpCode().equals(request.getOtp())) {
            token.setAttemptCount(token.getAttemptCount() + 1);
            tokenRepository.save(token);
            auditLogService.failure(AuditAction.OTP_FAILED, user.getId(), email,
                    "wrong otp, attempt " + token.getAttemptCount());
            throw new InvalidOtpException("Wrong OTP for user");
        }

        token.setUsed(true);
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        auditLogService.success(AuditAction.EMAIL_VERIFIED, user.getId(), email);
    }
}
