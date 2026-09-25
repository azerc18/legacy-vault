package com.ltld.app.legacyvault.service.verificationservice;

import com.ltld.app.legacyvault.dto.otpdto.SendOtpRequest;
import com.ltld.app.legacyvault.dto.otpdto.VerifyOtpRequest;
import com.ltld.app.legacyvault.dto.entity.User;
import com.ltld.app.legacyvault.dto.entity.VerificationToken;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.ExpiredOtpException;
import com.ltld.app.legacyvault.exception.InvalidOtpException;
import com.ltld.app.legacyvault.exception.OtpNotFoundException;
import com.ltld.app.legacyvault.exception.TooManyAttemptsException;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationTokenRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VerificationTokenServiceImpl implements VerificationTokenService {
    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final OtpGenerator otpGenerator;
    private final EmailSender emailSender;

    private static final int MAX_ATTEMPTS = 5;
    private static final int OTP_TTL = 300;

    @Override
    @Transactional
    public void sendOtp(SendOtpRequest request) {
        Optional<User> userOtp = userRepository.findByEmail(request.getEmail());

        if(userOtp.isEmpty()){
            return;
        }

        User user = userOtp.get();

        tokenRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user)
                .ifPresent(oldToken -> {
                    oldToken.setUsed(true);
                    tokenRepository.save(oldToken);
                });

        if (user.getStatus() == UserStatus.ACTIVE) {
            return;
        }

        String otp = otpGenerator.generate();

        VerificationToken token = VerificationToken.builder()
                .otpCode(otp)
                .expiresAt(Instant.now().plusSeconds(OTP_TTL))
                .isUsed(false)
                .attemptCount(0)
                .createdAt(Instant.now())
                .user(user)
                .build();


        tokenRepository.save(token);
        emailSender.sendEmail(request.getEmail(), otp);
    }

    @Override
    @Transactional
    public void verifyEmail(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(()-> new OtpNotFoundException("User not found"));


        VerificationToken token = tokenRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user)
                .orElseThrow(()-> new OtpNotFoundException("OTP not found"));

        if(token.getAttemptCount() >= MAX_ATTEMPTS){
            throw new TooManyAttemptsException("Too many failed attempts");
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ExpiredOtpException("OTP expired for user");
        }

        if(!token.getOtpCode().equals(request.getOtp())){
            token.setAttemptCount(token.getAttemptCount() + 1);
            tokenRepository.save(token);
            throw new InvalidOtpException("Wrong OTP for user");
        }

        token.setUsed(true);
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }
}
