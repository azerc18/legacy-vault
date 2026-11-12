package com.ltld.app.legacyvault.service.userservice;

import com.ltld.app.legacyvault.dto.userdto.AvatarFile;
import com.ltld.app.legacyvault.dto.userdto.ChangePasswordRequest;
import com.ltld.app.legacyvault.dto.userdto.SetPasswordRequest;
import com.ltld.app.legacyvault.dto.userdto.UpdateProfileRequest;
import com.ltld.app.legacyvault.dto.userdto.UserProfileResponse;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AppLanguage;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.KycLevel;
import com.ltld.app.legacyvault.enums.TokenType;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.exception.ExpiredOtpException;
import com.ltld.app.legacyvault.exception.InvalidOtpException;
import com.ltld.app.legacyvault.exception.OtpNotFoundException;
import com.ltld.app.legacyvault.exception.TooManyAttemptsException;
import com.ltld.app.legacyvault.exception.StorageUnavailableException;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import com.ltld.app.legacyvault.service.mfaservice.MfaService;
import com.ltld.app.legacyvault.service.verificationservice.VerificationTokenService;
import com.ltld.app.legacyvault.utility.VnPhoneNumber;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    static final long MAX_AVATAR_SIZE = 2L * 1024 * 1024; // 2MB
    private static final Set<String> AVATAR_TYPES = Set.of("image/png", "image/jpeg");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;
    private final CryptoService cryptoService;
    private final VerificationTokenService verificationTokenService;
    private final MfaService mfaService;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMe(UUID userId) {
        return toResponse(load(userId));
    }

    @Override
    @Transactional
    public UserProfileResponse updateMe(UUID userId, UpdateProfileRequest request) {
        User user = load(userId);
        String newName = request.getFullName().trim();
        String newPhone = normalizePhoneIfChanged(user.getPhoneNumber(), request.getPhoneNumber());
        // Đã xác minh danh tính: họ tên và số điện thoại gắn với CCCD / OTP, chỉ đổi qua trang Xác minh danh tính
        if (user.getKycLevel() != null && user.getKycLevel() != KycLevel.NONE
                && (!newName.equals(user.getFullName()) || !Objects.equals(newPhone, user.getPhoneNumber()))) {
            throw new UserProfileException(ErrorCode.PROFILE_LOCKED_BY_KYC,
                    "Your identity is verified; update your name or phone number from the identity verification page");
        }
        user.setFullName(newName);
        if (!Objects.equals(newPhone, user.getPhoneNumber())) {
            user.setPhoneNumber(newPhone);
            user.setPhoneVerifiedAt(null);
        }
        if (request.getPreferredLanguage() != null) {
            user.setPreferredLanguage(AppLanguage.fromTag(request.getPreferredLanguage()));
        }
        userRepository.save(user);

        auditLogService.success(AuditAction.PROFILE_UPDATED, user.getId(), user.getEmail());
        return toResponse(user);
    }

    // noRollbackFor: lần nhập sai vẫn phải giữ lại bản ghi audit
    @Override
    @Transactional(noRollbackFor = UserProfileException.class)
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserProfileException("Account not found", HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            auditLogService.failure(AuditAction.PASSWORD_CHANGE_FAILED, user.getId(), user.getEmail(), "wrong current password");
            throw new UserProfileException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new UserProfileException("The new password must be different from the current password", HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordSet(true);
        userRepository.save(user);

        // Đăng xuất mọi thiết bị khác, giống luồng reset-password
        tokenService.revokeAllUserTokens(user.getId(), RevokedReason.PASSWORD_CHANGED);
        auditLogService.success(AuditAction.PASSWORD_CHANGED, user.getId(), user.getEmail());
    }

    // Bước 1 của cài đặt mật khẩu: gửi OTP tới email (cùng mã, hạn, giới hạn gửi như "Quên mật khẩu").
    @Override
    @Transactional
    public void sendSetPasswordOtp(UUID userId) {
        User user = load(userId);
        requireNoPassword(user);
        verificationTokenService.issueOtp(user, TokenType.PASSWORD_RESET);
    }

    // Bước 2: tài khoản tạo bằng Google chưa có mật khẩu (chuỗi ngẫu nhiên) -> cài đặt mật khẩu, không hỏi mật khẩu hiện tại.
    // Chỉ có phiên đăng nhập là chưa đủ (máy dùng chung, token lộ): phải có OTP email, và mã 2FA nếu đã bật.
    // Không thu hồi phiên: trước đó không có mật khẩu nào để bị lộ, người dùng vẫn ở lại trang hồ sơ.
    // noRollbackFor: lần nhập OTP / 2FA sai vẫn được đếm và ghi audit
    @Override
    @Transactional(noRollbackFor = {UserProfileException.class, InvalidOtpException.class,
            TooManyAttemptsException.class, ExpiredOtpException.class, OtpNotFoundException.class})
    public UserProfileResponse setPassword(UUID userId, SetPasswordRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserProfileException("Account not found", HttpStatus.NOT_FOUND));
        requireNoPassword(user);

        verificationTokenService.consumeOtp(user, TokenType.PASSWORD_RESET, request.getOtp(),
                AuditAction.PASSWORD_CHANGE_FAILED);
        if (user.isMfaEnabled() && !mfaService.verifyCode(user, request.getCode())) {
            auditLogService.failure(AuditAction.PASSWORD_CHANGE_FAILED, user.getId(), user.getEmail(),
                    "set password: invalid 2fa code");
            throw new UserProfileException("The two-step verification code is incorrect", HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordSet(true);
        userRepository.save(user);
        auditLogService.log(AuditAction.PASSWORD_CHANGED, AuditResult.SUCCESS, user.getId(), user.getEmail(),
                null, null, null, "password set (google account)");
        return toResponse(user);
    }

    private static void requireNoPassword(User user) {
        if (user.isPasswordSet()) {
            // Đã có mật khẩu thì phải đổi mật khẩu (có kiểm tra mật khẩu hiện tại)
            throw new UserProfileException("A password is already set; use change password instead", HttpStatus.CONFLICT);
        }
    }

    // ---------------- Avatar ----------------

    @Override
    @Transactional
    public UserProfileResponse updateAvatar(UUID userId, MultipartFile file) {
        byte[] bytes = readAndValidateAvatar(file);
        User user = load(userId);

        String newPath;
        try {
            // H1: mã hóa như mọi file khác, để object trên bucket luôn là bản mã
            newPath = fileStorageService.storeFile(file, cryptoService.encryptBytes(bytes));
        } catch (StorageUnavailableException e) {
            throw e; // H1: storage mất kết nối -> 503 STORAGE_UNAVAILABLE
        } catch (Exception e) {
            throw new UserProfileException("Could not save the avatar", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String oldPath = user.getAvatarPath();
        user.setAvatarPath(newPath);
        user.setAvatarUpdatedAt(Instant.now());
        userRepository.save(user);

        // Commit thành công mới xoá ảnh cũ; rollback thì xoá ảnh mới vừa ghi để không để rác
        runAfterTransaction(
                () -> fileStorageService.deleteFile(oldPath),
                () -> fileStorageService.deleteFile(newPath));

        auditLogService.success(AuditAction.AVATAR_UPDATED, user.getId(), user.getEmail());
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AvatarFile getAvatar(UUID userId) {
        User user = load(userId);
        if (user.getAvatarPath() == null) {
            throw new UserProfileException("No avatar yet", HttpStatus.NOT_FOUND);
        }
        try {
            byte[] content = decryptAvatar(fileStorageService.readFile(user.getAvatarPath()));
            return new AvatarFile(content, UploadedDocumentValidator.detectContentType(content));
        } catch (IOException | SecurityException e) {
            throw new UserProfileException("Avatar not found", HttpStatus.NOT_FOUND);
        }
    }

    // Avatar lưu trước H1 là ảnh gốc (chưa mã hóa): giải mã không được thì dùng nguyên bytes đã lưu
    private byte[] decryptAvatar(byte[] stored) {
        try {
            return cryptoService.decryptBytes(stored);
        } catch (GeneralSecurityException e) {
            return stored;
        } catch (Exception e) {
            throw new UserProfileException("Could not read the avatar", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public UserProfileResponse deleteAvatar(UUID userId) {
        User user = load(userId);
        String oldPath = user.getAvatarPath();
        if (oldPath == null) return toResponse(user); // không có gì để xoá

        user.setAvatarPath(null);
        user.setAvatarUpdatedAt(Instant.now());
        userRepository.save(user);

        runAfterTransaction(() -> fileStorageService.deleteFile(oldPath), null);
        auditLogService.success(AuditAction.AVATAR_DELETED, user.getId(), user.getEmail());
        return toResponse(user);
    }

    // Chỉ nhận PNG/JPG thật (kiểm tra magic bytes), tối đa 2MB
    private byte[] readAndValidateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UserProfileException("Please choose an image", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new UserProfileException("Image must be at most 2MB", HttpStatus.BAD_REQUEST);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UserProfileException("Could not read the image file", HttpStatus.BAD_REQUEST);
        }
        if (!AVATAR_TYPES.contains(UploadedDocumentValidator.detectContentType(bytes))) {
            throw new UserProfileException("Only PNG or JPG images are accepted", HttpStatus.BAD_REQUEST);
        }
        return bytes;
    }

    // Chạy việc dọn file sau khi transaction kết thúc (không có transaction -> chạy luôn onCommit)
    private static void runAfterTransaction(Runnable onCommit, Runnable onRollback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            onCommit.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) onCommit.run();
                else if (onRollback != null) onRollback.run();
            }
        });
    }

    /**
     * Trả số sẽ lưu. Số không đổi so với DB (kể cả số cũ chưa đúng chuẩn) thì giữ nguyên để không chặn việc sửa tên;
     * số mới phải là số di động Việt Nam hợp lệ và được lưu dạng E.164.
     */
    static String normalizePhoneIfChanged(String stored, String requested) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        String trimmed = requested.trim();
        if (trimmed.equals(stored)) {
            return stored;
        }
        try {
            String e164 = VnPhoneNumber.parse(trimmed).e164();
            if (stored != null && VnPhoneNumber.tryParse(stored).map(VnPhoneNumber::e164).filter(e164::equals).isPresent()) {
                return stored;   // cùng số, chỉ khác cách viết
            }
            return e164;
        } catch (VnPhoneNumber.InvalidVnPhoneException e) {
            throw new UserProfileException(ErrorCode.PHONE_INVALID, e.getMessage());
        }
    }

    private User load(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserProfileException("Account not found", HttpStatus.NOT_FOUND));
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
                .hasAvatar(u.getAvatarPath() != null)
                .avatarUpdatedAt(u.getAvatarUpdatedAt())
                .mfaEnabled(u.isMfaEnabled())
                .passwordSet(u.isPasswordSet())
                .kycLevel((u.getKycLevel() != null ? u.getKycLevel() : KycLevel.NONE).name())
                .phoneVerified(u.getPhoneVerifiedAt() != null)
                .preferredLanguage((u.getPreferredLanguage() != null ? u.getPreferredLanguage() : AppLanguage.DEFAULT).tag())
                .build();
    }
}
