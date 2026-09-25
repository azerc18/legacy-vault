package com.ltld.app.legacyvault.verificationtokentest;

import com.ltld.app.legacyvault.dto.entity.User;
import com.ltld.app.legacyvault.dto.entity.VerificationToken;
import com.ltld.app.legacyvault.dto.otpdto.SendOtpRequest;
import com.ltld.app.legacyvault.dto.otpdto.VerifyOtpRequest;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.ExpiredOtpException;
import com.ltld.app.legacyvault.exception.InvalidOtpException;
import com.ltld.app.legacyvault.exception.OtpNotFoundException;
import com.ltld.app.legacyvault.exception.TooManyAttemptsException;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationTokenRepository;
import com.ltld.app.legacyvault.service.verificationservice.VerificationTokenServiceImpl;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @InjectMocks
    private VerificationTokenServiceImpl tokenService;

    private User pendingUser() {
        return User.builder()
                .email("test@example.com")
                .status(UserStatus.PENDING)
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
                .build();
    }

    @Test
    void sendOtp_userNotFound_doesNothing() {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail("notfound@example.com");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        tokenService.sendOtp(request);

        verify(tokenRepository, never()).findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(any());
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
        when(tokenRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(activeUser))
                .thenReturn(Optional.empty());

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

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user))
                .thenReturn(Optional.of(oldToken));
        when(otpGenerator.generate()).thenReturn("654321");

        tokenService.sendOtp(request);

        assertThat(oldToken.isUsed()).isTrue();

        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository, times(2)).save(tokenCaptor.capture());
        // lan save dau: oldToken bi invalidate; lan save thu hai: token OTP moi
        VerificationToken newToken = tokenCaptor.getAllValues().get(1);
        assertThat(newToken.getOtpCode()).isEqualTo("654321");
        assertThat(newToken.getUser()).isEqualTo(user);

        verify(emailSender, times(1)).sendEmail(request.getEmail(), "654321");
    }

    @Test
    void sendOtp_pendingUserNoOldToken_generatesAndSendsOtp() {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail("test@example.com");
        User user = pendingUser();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user))
                .thenReturn(Optional.empty());
        when(otpGenerator.generate()).thenReturn("999999");

        tokenService.sendOtp(request);

        verify(tokenRepository, times(1)).save(any(VerificationToken.class));
        verify(emailSender, times(1)).sendEmail(request.getEmail(), "999999");
    }

    @Test
    void verifyEmail_userNotFound_throwsOtpNotFoundException() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("notfound@example.com");
        request.setOtp("123456");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.verifyEmail(request))
                .isInstanceOf(OtpNotFoundException.class)
                .hasMessage("User not found");

        verify(tokenRepository, never()).findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(any());
    }

    void verifyEmail_tokenNotFound_throwsOtpNotFoundException() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");
        User user = pendingUser();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user))
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
        when(tokenRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user))
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
        when(tokenRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user))
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
        when(tokenRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> tokenService.verifyEmail(request))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Wrong OTP for user");

        assertThat(token.getAttemptCount()).isEqualTo(3);
        verify(tokenRepository, times(1)).save(token);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_correctOtp_marksTokenUsedAndActivatesUser() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");
        User user = pendingUser();
        VerificationToken token = tokenFor(user, "123456", 0, Instant.now().plusSeconds(100));

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user))
                .thenReturn(Optional.of(token));

        tokenService.verifyEmail(request);

        assertThat(token.isUsed()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        verify(tokenRepository, times(1)).save(token);
        verify(userRepository, times(1)).save(user);
    }
}
