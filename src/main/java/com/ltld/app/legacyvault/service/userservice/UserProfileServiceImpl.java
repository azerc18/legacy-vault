package com.ltld.app.legacyvault.service.userservice;

import com.ltld.app.legacyvault.dto.userdto.AvatarFile;
import com.ltld.app.legacyvault.dto.userdto.ChangePasswordRequest;
import com.ltld.app.legacyvault.dto.userdto.UpdateProfileRequest;
import com.ltld.app.legacyvault.dto.userdto.UserProfileResponse;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.exception.StorageUnavailableException;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private static final byte CIPHER_VERSION = 1;
    static final long MAX_AVATAR_SIZE = 2L * 1024 * 1024; // 2MB
    private static final Set<String> AVATAR_TYPES = Set.of("image/png", "image/jpeg");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;
    private final CryptoService cryptoService;

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
                .orElseThrow(() -> new UserProfileException("Account not found", HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            auditLogService.failure(AuditAction.PASSWORD_CHANGE_FAILED, user.getId(), user.getEmail(), "wrong current password");
            throw new UserProfileException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new UserProfileException("The new password must be different from the current password", HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Đăng xuất mọi thiết bị khác, giống luồng reset-password
        tokenService.revokeAllUserTokens(user.getId(), RevokedReason.PASSWORD_CHANGED);
        auditLogService.success(AuditAction.PASSWORD_CHANGED, user.getId(), user.getEmail());
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

    // Avatar lưu trước H1 là ảnh gốc (PNG/JPG không bao giờ bắt đầu bằng byte 0x01 = version của CryptoServiceImpl):
    // không phải định dạng bản mã thì dùng nguyên. Bản mã bị sửa hoặc sai khóa vẫn trả 500, không trả bytes rác.
    private byte[] decryptAvatar(byte[] stored) {
        if (stored.length == 0 || stored[0] != CIPHER_VERSION) {
            return stored;
        }
        try {
            return cryptoService.decryptBytes(stored);
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
                .build();
    }
}
