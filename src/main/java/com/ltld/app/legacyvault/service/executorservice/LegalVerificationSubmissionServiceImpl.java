package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.SubmitLegalVerificationRequest;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationMessage;
import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.DuplicateVerificationRequestException;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.exception.InvalidDeathCertificateException;
import com.ltld.app.legacyvault.exception.InvalidVerificationRequestException;
import com.ltld.app.legacyvault.exception.VaultStateConflictException;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.repository.VerificationMessageRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Triển khai nghiệp vụ FR-09: Executor tải lên giấy chứng tử và gửi yêu cầu xác minh pháp lý
 * đến Legal Verifier để mở khóa Vault.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegalVerificationSubmissionServiceImpl implements LegalVerificationSubmissionService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB, khớp spring.servlet.multipart
    private static final String REFERENCE_TYPE = "LegalVerificationRequest";
    private static final String LEGAL_VERIFIER_ROLE = "LEGAL_VERIFIER";

    // Yêu cầu còn "mở": PENDING hoặc APPROVED nhưng chưa ký (FR-09 4a)
    private static final Set<LegalVerificationStatus> OPEN_STATUSES =
            Set.of(LegalVerificationStatus.PENDING, LegalVerificationStatus.APPROVED);

    // Đuôi tệp được chấp nhận (khớp mock kiểm tra tài liệu FR-13) và chữ ký nhị phân đầu tệp tương ứng
    private static final Map<String, byte[]> FILE_SIGNATURES = Map.of(
            ".pdf", new byte[]{0x25, 0x50, 0x44, 0x46},                                 // %PDF
            ".png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
            ".jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    private final VaultRepository vaultRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final UserRepository userRepository;
    private final LegalVerificationRequestRepository requestRepository;
    private final VerificationRequestVaultRepository requestVaultRepository;
    private final VerificationMessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final FileStorageService fileStorageService;
    private final CryptoService cryptoService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public VerificationRequestResponseDto submit(SubmitLegalVerificationRequest request, String executorLogin) {
        // 2a: kiểm tra tệp trước mọi truy vấn DB
        MultipartFile file = request.getDeathCertificate();
        byte[] content = readAndValidateDeathCertificate(file);

        // Khóa Vault là truy vấn đầu tiên của transaction: với REPEATABLE READ của MySQL,
        // snapshot được tạo sau khi có khóa nên kiểm tra trùng lặp bên dưới thấy được yêu cầu vừa commit.
        List<UUID> vaultIds = request.getVaultIds().stream().distinct().toList();
        List<Vault> vaults = vaultRepository.findAllByIdInForUpdate(vaultIds);

        User executor = findUserByLogin(executorLogin)
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        // Vault không tồn tại và Vault không được ủy quyền trả cùng một lỗi để không lộ Vault của người khác
        if (vaults.size() != vaultIds.size()) {
            throw new ExecutorAssignmentNotFoundException("Executor not assigned to this vault.");
        }

        Set<UUID> ownerIds = vaults.stream().map(v -> v.getOwner().getId()).collect(Collectors.toSet());
        if (ownerIds.size() > 1) {
            throw new InvalidVerificationRequestException(
                    "Các Vault trong một yêu cầu xác minh phải thuộc cùng một chủ sở hữu.");
        }
        User owner = vaults.getFirst().getOwner();

        validateExecutorAssignment(executor, owner.getId());

        List<UUID> notActive = vaults.stream()
                .filter(v -> v.getStatus() != VaultStatus.ACTIVE)
                .map(Vault::getId)
                .toList();
        if (!notActive.isEmpty()) {
            throw new VaultStateConflictException(
                    "Chỉ Vault đang ở trạng thái ACTIVE mới được gửi yêu cầu mở khóa: " + notActive);
        }

        if (requestVaultRepository.existsByVaultIdInAndRequestStatusIn(vaultIds, OPEN_STATUSES)) {
            throw new DuplicateVerificationRequestException(
                    "Đã tồn tại yêu cầu xác minh pháp lý đang chờ xử lý cho Vault được chọn.");
        }

        // Bước 4: mã hóa nội dung tệp rồi lưu; đường dẫn giữ nguyên đuôi tệp cho bước kiểm tra tài liệu FR-13
        String storedPath = storeEncrypted(file, content);

        LegalVerificationRequest saved = requestRepository.saveAndFlush(LegalVerificationRequest.builder()
                .owner(owner)
                .executor(executor)
                .deathCertificateFileUrlEncrypted(storedPath)
                .build());

        requestVaultRepository.saveAll(vaults.stream()
                .map(vault -> VerificationRequestVault.builder().request(saved).vault(vault).build())
                .toList());

        // Ghi chú (nếu có) là tin nhắn đầu tiên của luồng trao đổi với Legal Verifier
        if (request.getNote() != null && !request.getNote().isBlank()) {
            messageRepository.save(VerificationMessage.builder()
                    .request(saved)
                    .sender(executor)
                    .messageText(request.getNote().trim())
                    .build());
        }

        auditLogService.log(AuditAction.LEGAL_REQUEST_SUBMITTED, AuditResult.SUCCESS,
                executor.getId(), executor.getEmail(),
                REFERENCE_TYPE, saved.getId().toString(), "vaultIds=" + vaultIds);

        notifyLegalVerifiers(saved, executor, owner);

        log.info("FR-09: executor {} submitted legal verification request {} for vaults {}",
                executor.getId(), saved.getId(), vaultIds);

        return VerificationRequestResponseDto.builder()
                .id(saved.getId())
                .ownerName(owner.getFullName())
                .executorName(executor.getFullName())
                .status(saved.getStatus())
                .hasDeathCertificate(true)
                .vaultIds(vaultIds)
                .createdAt(saved.getCreatedAt())
                .build();
    }

    private byte[] readAndValidateDeathCertificate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDeathCertificateException("Vui lòng tải lên giấy chứng tử.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidDeathCertificateException("Tệp giấy chứng tử vượt quá dung lượng cho phép (10MB).");
        }

        String filename = file.getOriginalFilename();
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot).toLowerCase(Locale.ROOT);
        byte[] signature = FILE_SIGNATURES.get(extension);
        if (signature == null) {
            throw new InvalidDeathCertificateException(
                    "Định dạng tệp không hợp lệ. Chỉ chấp nhận .pdf, .jpg, .png.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InvalidDeathCertificateException("Không đọc được tệp giấy chứng tử. Vui lòng tải lại.");
        }

        // Nội dung phải khớp đuôi tệp, tránh đổi tên tệp bất kỳ thành .pdf
        if (content.length < signature.length
                || !Arrays.equals(content, 0, signature.length, signature, 0, signature.length)) {
            throw new InvalidDeathCertificateException(
                    "Nội dung tệp không khớp định dạng " + extension + ". Vui lòng tải lại.");
        }
        return content;
    }

    private void validateExecutorAssignment(User executor, UUID ownerId) {
        List<ExecutorAssignment> ownerAssignments = executorAssignmentRepository
                .findAllByExecutorUserId(executor.getId()).stream()
                .filter(a -> a.getOwner().getId().equals(ownerId))
                .toList();

        if (ownerAssignments.isEmpty()) {
            throw new ExecutorAssignmentNotFoundException("Executor not assigned to this vault.");
        }
        if (ownerAssignments.stream().noneMatch(a -> a.getStatus() == ExecutorStatus.ACTIVE)) {
            throw new ExecutorNotActiveException("Access denied. Executor has not been activated yet.");
        }
    }

    private String storeEncrypted(MultipartFile file, byte[] content) {
        String storedPath;
        try {
            storedPath = fileStorageService.storeFile(file, cryptoService.encryptBytes(content));
        } catch (Exception e) {
            throw new IllegalStateException("Không thể lưu giấy chứng tử.", e);
        }

        // Tệp đã ghi xuống đĩa không nằm trong transaction: xóa nếu transaction rollback
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        fileStorageService.deleteFile(storedPath);
                    }
                }
            });
        }
        return storedPath;
    }

    // Bước 5: thông báo trong ứng dụng cho Legal Verifier, bỏ qua người có xung đột lợi ích
    private void notifyLegalVerifiers(LegalVerificationRequest request, User executor, User owner) {
        List<Notification> notifications = userRepository.findAllByRolesCode(LEGAL_VERIFIER_ROLE).stream()
                .filter(verifier -> !verifier.getId().equals(executor.getId())
                        && !verifier.getId().equals(owner.getId()))
                .map(verifier -> Notification.builder()
                        .recipient(verifier)
                        .type(NotificationType.VERIFICATION_REQUEST)
                        .channel(NotificationChannel.IN_APP)
                        .referenceType(REFERENCE_TYPE)
                        .referenceId(request.getId())
                        .content("Có yêu cầu xác minh pháp lý mới đang chờ xét duyệt.")
                        .build())
                .toList();
        notificationRepository.saveAll(notifications);
    }

    private Optional<User> findUserByLogin(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        Optional<User> byEmail = userRepository.findByEmail(login);
        if (byEmail.isPresent()) {
            return byEmail;
        }
        try {
            UUID userId = UUID.fromString(login);
            return userRepository.findById(userId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
