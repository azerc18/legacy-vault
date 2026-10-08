package com.ltld.app.legacyvault.verificationtokentest;

import com.ltld.app.legacyvault.dto.forgotpassworddto.ForgotPasswordRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.VerificationToken;
import com.ltld.app.legacyvault.dto.otpdto.SendOtpRequest;
import com.ltld.app.legacyvault.dto.otpdto.VerifyOtpRequest;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.TokenType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.*;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationTokenRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.verificationservice.VerificationTokenServiceImpl;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VerificationTokenServiceImplTest {
    @Mock
    private VerificationTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OtpGenerator otpGenerator;

    @Mock
    private EmailSender emailSender;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private VerificationTokenServiceImpl tokenService;

    private User pendingUser() {
        return User.builder()
                .email("test@example.com")
                .status(UserStatus.PENDING)
                .build();
    }

    private User activeUser() {
        return User.builder()
                .email("test@example.com")
                .status(UserStatus.ACTIVE)
                .build();
    }

    private VerificationToken tokenFor(User user, String otp, int attemptCount, Instant expiresAt) {
        return VerificationToken.builder()
                .otpCode(otp)
                .isUsed(false)
                .attemptCount(attemptCount)
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .user(user)
                .type(TokenType.EMAIL_VERIFICATION)
                .build();
    }

    @Test
    void sendOtp_userNotFound_doesNothing() {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail("notfound@example.com");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        tokenService.sendOtp(request);

        verify(tokenRepository, never())
                .findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(any(), any());
        verify(tokenRepository, never()).save(any());
        verify(otpGenerator, never()).generate();
        verify(emailSender, never()).sendEmail(anyString(), anyString());
    }

    @Test
    void sendOtp_userAlreadyActive_doesNotGenerateNewOtp() {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail("test@example.com");
        User activeUser = User.builder().email("test@example.com").status(UserStatus.ACTIVE).build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(activeUser));

        tokenService.sendOtp(request);

        verify(otpGenerator, never()).generate();
        verify(emailSender, never()).sendEmail(anyString(), anyString());
        // save() chi duoc goi neu co old token bi invalidate; o day khong co old token
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void sendOtp_pendingUserWithOldToken_invalidatesOldTokenAndSendsNewOtp() {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail("test@example.com");
        User user = pendingUser();
        VerificationToken oldToken = tokenFor(user, "111111", 0, Instant.now().plusSeconds(100));
        oldToken.setCreatedAt(Instant.now().minusSeconds(120));   // quá cooldown 60s

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(oldToken));
        when(otpGenerator.generate()).thenReturn("654321");

        tokenService.sendOtp(request);

        assertThat(oldToken.isUsed()).isTrue();

        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository, times(2)).save(tokenCaptor.capture());
        VerificationToken newToken = tokenCaptor.getAllValues().get(1);
        assertThat(newToken.getOtpCode()).isEqualTo("654321");
        assertThat(newToken.getUser()).isEqualTo(user);
        assertThat(newToken.getType()).isEqualTo(TokenType.EMAIL_VERIFICATION);

        verify(emailSender, times(1)).sendEmail(request.getEmail(), "654321");
        verify(tokenRepository, times(2))
                .findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.EMAIL_VERIFICATION);
    }

    @Test
    void sendOtp_pendingUserNoOldToken_generatesAndSendsOtp() {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail("test@example.com");
        User user = pendingUser();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.empty());
        when(otpGenerator.generate()).thenReturn("999999");

        tokenService.sendOtp(request);

        verify(tokenRepository, times(1)).save(any(VerificationToken.class));
        verify(emailSender, times(1)).sendEmail(request.getEmail(), "999999");
    }

    @Test
    void sendOtp_withinCooldown_throwsTooManyRequests_andDoesNotSendEmail() {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail("test@example.com");
        User user = pendingUser();
        VerificationToken last = tokenFor(user, "111111", 0, Instant.now().plusSeconds(100));
        last.setCreatedAt(Instant.now().minusSeconds(20));       // mới gửi 20s trước

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.EMAIL_VERIFICATION)).thenReturn(Optional.of(last));

        assertThatThrownBy(() -> tokenService.sendOtp(request))
                .isInstanceOf(TooManyOtpRequestsException.class);

        verify(tokenRepository, never()).save(any());
        verify(otpGenerator, never()).generate();
        verify(emailSender, never()).sendEmail(anyString(), anyString());
        verify(auditLogService).failure(eq(AuditAction.OTP_RATE_LIMITED), any(), eq("test@example.com"), eq("cooldown"));
    }

    @Test
    void sendOtp_afterCooldown_sendsNewOtp() {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail("test@example.com");
        User user = pendingUser();
        VerificationToken last = tokenFor(user, "111111", 0, Instant.now().plusSeconds(100));
        last.setCreatedAt(Instant.now().minusSeconds(61));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.EMAIL_VERIFICATION)).thenReturn(Optional.of(last));
        when(otpGenerator.generate()).thenReturn("222222");

        tokenService.sendOtp(request);

        verify(emailSender).sendEmail("test@example.com", "222222");
    }

    @Test
    void sendOtp_hourlyLimitReached_throwsTooManyRequests() {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail("test@example.com");
        User user = pendingUser();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.countByUserAndTypeAndCreatedAtAfter(eq(user), eq(TokenType.EMAIL_VERIFICATION), any(Instant.class))).thenReturn(5L);

        assertThatThrownBy(() -> tokenService.sendOtp(request))
                .isInstanceOf(TooManyOtpRequestsException.class);

        verify(emailSender, never()).sendEmail(anyString(), anyString());
        verify(auditLogService).failure(eq(AuditAction.OTP_RATE_LIMITED), any(), eq("test@example.com"), eq("hourly limit"));
    }

    @Test
    void verifyEmail_userNotFound_throwsOtpNotFoundException() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("notfound@example.com");
        request.setOtp("123456");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.verifyEmail(request))
                .isInstanceOf(OtpNotFoundException.class)
                .hasMessage("OTP not found");

        verify(tokenRepository, never())
                .findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void verifyEmail_tokenNotFound_throwsOtpNotFoundException() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");
        User user = pendingUser();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.verifyEmail(request))
                .isInstanceOf(OtpNotFoundException.class)
                .hasMessage("OTP not found");
    }

    @Test
    void verifyEmail_tooManyAttempts_throwsTooManyAttemptsException() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");
        User user = pendingUser();
        // MAX_ATTEMPTS = 5 trong service, dat attemptCount = 5 de vuot nguong
        VerificationToken token = tokenFor(user, "654321", 5, Instant.now().plusSeconds(100));

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> tokenService.verifyEmail(request))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasMessage("Too many failed attempts");

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void verifyEmail_expiredOtp_throwsExpiredOtpException() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");
        User user = pendingUser();
        VerificationToken token = tokenFor(user, "123456", 0, Instant.now().minusSeconds(10));

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> tokenService.verifyEmail(request))
                .isInstanceOf(ExpiredOtpException.class)
                .hasMessage("OTP expired for user");

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void verifyEmail_wrongOtp_throwsInvalidOtpException_andIncrementsAttemptCount() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("000000");
        User user = pendingUser();
        VerificationToken token = tokenFor(user, "123456", 2, Instant.now().plusSeconds(100));

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> tokenService.verifyEmail(request))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Wrong OTP for user");

        assertThat(token.getAttemptCount()).isEqualTo(3);
        verify(tokenRepository, times(1)).saveAndFlush(token);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_lockedUser_throwsLockedAccountException() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("000000");
        User user = activeUser();
        user.setStatus(UserStatus.LOCKED);
        VerificationToken token = tokenFor(user, "123456", 0, Instant.now().plusSeconds(100));

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> tokenService.verifyEmail(request))
                .isInstanceOf(LockedAccountException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_pendingUserWithExpiredLockedUntil_succeeds() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");
        User user = pendingUser();
        user.setLockedUntil(Instant.now().minusSeconds(100));

        VerificationToken token = tokenFor(user, "123456", 0, Instant.now().plusSeconds(100));

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        tokenService.verifyEmail(request);

        assertThat(token.isUsed()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        verify(tokenRepository, times(1)).saveAndFlush(token);
        verify(userRepository, times(1)).save(user);
    }



    @Test
    void verifyEmail_correctOtp_marksTokenUsedAndActivatesUser() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");
        User user = pendingUser();
        VerificationToken token = tokenFor(user, "123456", 0, Instant.now().plusSeconds(100));

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        tokenService.verifyEmail(request);

        assertThat(token.isUsed()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        verify(tokenRepository, times(1)).saveAndFlush(token);
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void sendPasswordResetOtp_userNotFound_doesNothing() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        tokenService.sendPasswordResetOtp(new ForgotPasswordRequest("notfound@example.com"));

        verify(otpGenerator, never()).generate();
        verify(tokenRepository, never()).save(any());
        verify(emailSender, never()).sendPasswordResetEmail(anyString(), anyString());
        verify(auditLogService).failure(eq(AuditAction.PASSWORD_RESET_REQUEST), isNull(),
                eq("notfound@example.com"), anyString());
    }

    @Test
    void sendPasswordResetOtp_pendingUser_doesNothing() {
        User user = pendingUser();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        tokenService.sendPasswordResetOtp(new ForgotPasswordRequest("test@example.com"));

        verify(otpGenerator, never()).generate();
        verify(tokenRepository, never()).save(any());
        verify(emailSender, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void sendPasswordResetOtp_activeUser_savesResetTokenAndSendsResetEmail() {
        User user = activeUser();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(otpGenerator.generate()).thenReturn("777777");

        tokenService.sendPasswordResetOtp(new ForgotPasswordRequest("  Test@Example.com "));

        ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TokenType.PASSWORD_RESET);
        assertThat(captor.getValue().getOtpCode()).isEqualTo("777777");

        verify(emailSender).sendPasswordResetEmail("test@example.com", "777777");
        verify(emailSender, never()).sendEmail(anyString(), anyString());
        verify(auditLogService).success(AuditAction.PASSWORD_RESET_REQUEST, user.getId(), "test@example.com");
    }

    @Test
    void sendPasswordResetOtp_rateLimited_returnsSilently() {
        User user = activeUser();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.countByUserAndTypeAndCreatedAtAfter(
                eq(user), eq(TokenType.PASSWORD_RESET), any(Instant.class))).thenReturn(5L);

        assertThatCode(() -> tokenService.sendPasswordResetOtp(new ForgotPasswordRequest("test@example.com")))
                .doesNotThrowAnyException();

        verify(otpGenerator, never()).generate();
        verify(tokenRepository, never()).save(any());
        verify(emailSender, never()).sendPasswordResetEmail(anyString(), anyString());
        verify(auditLogService).failure(eq(AuditAction.OTP_RATE_LIMITED), any(),
                eq("test@example.com"), eq("hourly limit"));
        verify(auditLogService, never()).success(eq(AuditAction.PASSWORD_RESET_REQUEST), any(), any());
    }



    @Test
    void issueOtp_emailVerification_activeUser_returnsEarly() {
        tokenService.issueOtp(activeUser(), TokenType.EMAIL_VERIFICATION);

        verifyNoInteractions(tokenRepository, otpGenerator, emailSender);
    }

    @Test
    void issueOtp_passwordReset_activeUser_stillSendsOtp() {
        User user = activeUser();
        when(otpGenerator.generate()).thenReturn("888888");

        tokenService.issueOtp(user, TokenType.PASSWORD_RESET);

        verify(tokenRepository, times(2))
                .findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.PASSWORD_RESET);
        verify(emailSender).sendPasswordResetEmail("test@example.com", "888888");
    }

    @Test
    void issueOtp_lockedUser_throwsLockedAccountException() {
        User user = activeUser();
        user.setStatus(UserStatus.LOCKED);

        assertThatThrownBy(() -> tokenService.issueOtp(user, TokenType.EMAIL_VERIFICATION))
                .isInstanceOf(LockedAccountException.class);

        verifyNoInteractions(tokenRepository, otpGenerator, emailSender);
    }

    @Test
    void consumeOtp_passwordReset_wrongOtp_usesResetTypeAndFailAction() {
        User user = activeUser();
        VerificationToken token = tokenFor(user, "123456", 0, Instant.now().plusSeconds(100));
        token.setType(TokenType.PASSWORD_RESET);

        when(tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> tokenService.consumeOtp(user, TokenType.PASSWORD_RESET,
                "000000", AuditAction.PASSWORD_RESET_FAILED))
                .isInstanceOf(InvalidOtpException.class);

        assertThat(token.getAttemptCount()).isEqualTo(1);

        verify(auditLogService).failure(eq(AuditAction.PASSWORD_RESET_FAILED), any(),
                eq("test@example.com"), eq("wrong otp, attempt 1"));
        verify(tokenRepository, never())
                .findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(any(), eq(TokenType.EMAIL_VERIFICATION));
        verify(tokenRepository).saveAndFlush(token);
    }

    @Test
    void consumeOtp_concurrentUpdate_throwsOptimisticLock_andDoesNotAuditWrongOtp() {
        User user = pendingUser();
        VerificationToken token = tokenFor(user, "123456", 0, Instant.now().plusSeconds(100));

        when(tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));
        when(tokenRepository.saveAndFlush(token))
                .thenThrow(new ObjectOptimisticLockingFailureException(VerificationToken.class, UUID.randomUUID()));

        assertThatThrownBy(() -> tokenService.consumeOtp(user, TokenType.EMAIL_VERIFICATION,
                "000000", AuditAction.OTP_FAILED))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(auditLogService, never()).failure(any(), any(), any(), startsWith("wrong otp"));
    }

    @Test
    void verifyEmail_concurrentConsume_doesNotActivateUser() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");
        User user = pendingUser();
        VerificationToken token = tokenFor(user, "123456", 0, Instant.now().plusSeconds(100));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(user, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));
        when(tokenRepository.saveAndFlush(token))
                .thenThrow(new ObjectOptimisticLockingFailureException(VerificationToken.class, UUID.randomUUID()));

        assertThatThrownBy(() -> tokenService.verifyEmail(request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(userRepository, never()).save(any());
        verify(auditLogService, never()).success(eq(AuditAction.EMAIL_VERIFIED), any(), any());
    }

}
