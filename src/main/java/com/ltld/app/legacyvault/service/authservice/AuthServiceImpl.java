package com.ltld.app.legacyvault.service.authservice;

import com.ltld.app.legacyvault.dto.logindto.LoginRequest;
import com.ltld.app.legacyvault.dto.logindto.LoginResponse;
import com.ltld.app.legacyvault.dto.logindto.LoginResult;
import com.ltld.app.legacyvault.dto.registerdto.RegisterRequest;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.EmailAlreadyExistsException;
import com.ltld.app.legacyvault.exception.InvalidCredentialException;
import com.ltld.app.legacyvault.exception.LockedAccountException;
import com.ltld.app.legacyvault.exception.NotActiveUserException;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final TokenService tokenService;

    private static final int MAX_FAILED_ATTEMPTS = 5;


    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if(userRepository.existsByEmail(request.getEmail())){
            throw new EmailAlreadyExistsException("Email already exists.");
        }

        Role defaultRole = roleRepository.findByCode("OWNER")
                .orElseThrow(()-> new RuntimeException("Default role not found in database."));

        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(defaultRole))
                .build();

        userRepository.save(user);
    }

    @Override
    @Transactional(noRollbackFor = {
            InvalidCredentialException.class,
            LockedAccountException.class
    })
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialException::new);

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new LockedAccountException();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setStatus(UserStatus.LOCKED);
                userRepository.save(user);
                throw new LockedAccountException();
            }
            userRepository.save(user);
            throw new InvalidCredentialException();
        }

        if (user.getStatus() == UserStatus.PENDING) {
            throw new NotActiveUserException();
        }

        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user, ipAddress, userAgent);

        LoginResponse response = LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresAt(tokenService.getAccessTokenExpiry())
                .build();

        return new LoginResult(response, refreshToken);
    }
}
