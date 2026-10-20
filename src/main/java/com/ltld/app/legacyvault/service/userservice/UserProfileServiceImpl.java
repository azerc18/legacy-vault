package com.ltld.app.legacyvault.service.userservice;

import com.ltld.app.legacyvault.dto.userdto.ChangePasswordRequest;
import com.ltld.app.legacyvault.dto.userdto.UpdateProfileRequest;
import com.ltld.app.legacyvault.dto.userdto.UserProfileResponse;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMe(UUID userId) {
        return toResponse(load(userId));
    }

    @Override
    @Transactional
    public UserProfileResponse updateMe(UUID userId, UpdateProfileRequest request) {
        User user = load(userId);
        user.setFullName(request.getFullName().trim());
        String phone = request.getPhoneNumber();
        user.setPhoneNumber(phone == null || phone.isBlank() ? null : phone.trim());
        userRepository.save(user);

        auditLogService.success(AuditAction.PROFILE_UPDATED, user.getId(), user.getEmail());
        return toResponse(user);
    }

    // noRollbackFor: lần nhập sai vẫn phải giữ lại bản ghi audit
    @Override
    @Transactional(noRollbackFor = UserProfileException.class)
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserProfileException("Không tìm thấy tài khoản", HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            auditLogService.failure(AuditAction.PASSWORD_CHANGE_FAILED, user.getId(), user.getEmail(), "wrong current password");
            throw new UserProfileException("Mật khẩu hiện tại không đúng", HttpStatus.BAD_REQUEST);
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new UserProfileException("Mật khẩu mới phải khác mật khẩu hiện tại", HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Đăng xuất mọi thiết bị khác, giống luồng reset-password
        tokenService.revokeAllUserTokens(user.getId(), RevokedReason.PASSWORD_CHANGED);
        auditLogService.success(AuditAction.PASSWORD_CHANGED, user.getId(), user.getEmail());
    }

    private User load(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserProfileException("Không tìm thấy tài khoản", HttpStatus.NOT_FOUND));
    }

    private static UserProfileResponse toResponse(User u) {
        return UserProfileResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .phoneNumber(u.getPhoneNumber())
                .status(u.getStatus())
                .roles(u.getRoles().stream().map(Role::getCode).sorted().toList())
                .createdAt(u.getCreatedAt())
                .lastLoginAt(u.getLastLoginAt())
                .build();
    }
}