package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.VerificationExchangeMessage;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ExchangeSenderRole;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.event.VerifierReplyPostedEvent;
import com.ltld.app.legacyvault.exception.InvalidVerificationDocumentException;
import com.ltld.app.legacyvault.exception.NotAssignedVerifierException;
import com.ltld.app.legacyvault.exception.VerificationRequestClosedException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationExchangeMessageRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifierMessageServiceImpl implements VerifierMessageService {

    private static final Set<LegalVerificationStatus> OPEN_STATUSES =
            EnumSet.of(LegalVerificationStatus.PENDING, LegalVerificationStatus.APPROVED);
    private static final int MAX_ATTACHMENT_NAME_LENGTH = 255;

    private final LegalVerificationRequestRepository requestRepository;
    private final VerificationExchangeMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UploadedDocumentValidator uploadedDocumentValidator;
    private final CryptoService cryptoService;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<VerificationMessageResponse> history(UUID requestId, UUID verifierId) {
        LegalVerificationRequest request = findAccessibleRequest(requestId, verifierId);
        return messageRepository.findHistory(request.getId()).stream()
                .map(m -> toResponse(m, m.getSender()))
                .toList();
    }

    @Override
    @Transactional
    public VerificationMessageResponse send(UUID requestId, UUID verifierId,
                                            MultipartFile file, SendVerificationMessageRequest body) {
        String content = normalize(body == null ? null : body.getContent());
        boolean hasFile = file != null && !file.isEmpty();
        if (content == null && !hasFile) {
            throw new InvalidVerificationDocumentException("Message content or attachment is required.");
        }

        byte[] encrypted = null;
        if (hasFile) {
            encrypted = encrypt(uploadedDocumentValidator.readAndValidate(file));
        }

        LegalVerificationRequest request = findAccessibleRequest(requestId, verifierId);
        if (!OPEN_STATUSES.contains(request.getStatus())) {
            throw new VerificationRequestClosedException("This verification request is closed.");
        }

        User verifier = userRepository.findById(verifierId)
                .orElseThrow(() -> new IllegalStateException("Verifier not found."));

        String storedPath = null;
        if (hasFile) {
            try {
                storedPath = fileStorageService.storeFile(file, encrypted);
            } catch (Exception e) {
                // bọc exception đã kiểm tra thành RuntimeException để transaction rollback
                throw new IllegalStateException("Failed to send verification message", e);
            }
            registerCleanupOnRollback(storedPath);
        }

        VerificationExchangeMessage saved = messageRepository.saveAndFlush(
                VerificationExchangeMessage.builder()
                        .request(request)
                        .sender(verifier)
                        .senderRole(ExchangeSenderRole.VERIFIER)
                        .content(content)
                        .attachmentPath(storedPath)
                        .attachmentName(hasFile ? sanitizeFileName(file.getOriginalFilename()) : null)
                        .attachmentSize(hasFile ? file.getSize() : null)
                        .build());

        auditLogService.log(AuditAction.VERIFICATION_MESSAGE_SENT, AuditResult.SUCCESS,
                verifierId, verifier.getEmail(),
                "LegalVerificationRequest", requestId.toString(),
                "role=VERIFIER, hasAttachment=" + hasFile);

        eventPublisher.publishEvent(
                new VerifierReplyPostedEvent(requestId, request.getExecutor().getId()));

        return toResponse(saved, verifier);
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationDocumentDto getAttachment(UUID requestId, UUID messageId, UUID verifierId) {
        findAccessibleRequest(requestId, verifierId);

        VerificationExchangeMessage message = messageRepository.findByIdAndRequestId(messageId, requestId)
                .filter(m -> m.getAttachmentPath() != null)
                .orElseThrow(() -> new VerificationRequestNotFoundException("Attachment not found."));

        byte[] plain;
        try {
            plain = cryptoService.decryptBytes(fileStorageService.readFile(message.getAttachmentPath()));
        } catch (Exception e) {
            log.error("Attachment read/decrypt failed for message {}", messageId, e);
            throw new IllegalStateException("Không thể đọc tệp đính kèm. Vui lòng thử lại sau.");
        }

        String email = userRepository.findById(verifierId).map(User::getEmail).orElse(null);
        auditLogService.log(AuditAction.VERIFICATION_ATTACHMENT_VIEWED, AuditResult.SUCCESS,
                verifierId, email, "VerificationExchangeMessage", messageId.toString(), null);

        String name = message.getAttachmentName() != null ? message.getAttachmentName() : "attachment";
        return VerificationDocumentDto.builder()
                .content(plain)
                .contentType(detectContentType(plain))
                .fileName(name)
                .build();
    }

    // Quy tắc quyền: xung đột lợi ích -> 404; PENDING ai cũng xem được; đã xử lý -> chỉ verifier được giao
    private LegalVerificationRequest findAccessibleRequest(UUID requestId, UUID verifierId) {
        LegalVerificationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new VerificationRequestNotFoundException("Không tìm thấy hồ sơ: " + requestId));

        boolean conflict = (request.getOwner() != null && verifierId.equals(request.getOwner().getId()))
                || (request.getExecutor() != null && verifierId.equals(request.getExecutor().getId()));
        if (conflict) {
            throw new VerificationRequestNotFoundException("Không tìm thấy hồ sơ: " + requestId);
        }

        if (request.getStatus() != LegalVerificationStatus.PENDING
                && (request.getVerifier() == null || !verifierId.equals(request.getVerifier().getId()))) {
            throw new NotAssignedVerifierException(
                    "Chỉ Legal Verifier đã xử lý hồ sơ mới được xem hoặc trả lời tin nhắn.");
        }
        return request;
    }

    private String detectContentType(byte[] b) {
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return "application/pdf";
        }
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private void registerCleanupOnRollback(String storedPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        fileStorageService.deleteFile(storedPath);
                    } catch (Exception ex) {
                        log.error("Failed to delete orphaned file after rollback: {}", storedPath, ex);
                    }
                }
            }
        });
    }

    private byte[] encrypt(byte[] plain) {
        try {
            return cryptoService.encryptBytes(plain);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send verification message", e);
        }
    }

    private static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String sanitizeFileName(String originalName) {
        if (originalName == null) {
            return null;
        }
        String name = originalName
                .substring(Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\')) + 1)
                .replaceAll("\\p{Cntrl}", "")
                .trim();
        if (name.isEmpty()) {
            return null;
        }
        return name.length() > MAX_ATTACHMENT_NAME_LENGTH
                ? name.substring(0, MAX_ATTACHMENT_NAME_LENGTH) : name;
    }

    private static VerificationMessageResponse toResponse(VerificationExchangeMessage m, User sender) {
        return VerificationMessageResponse.builder()
                .id(m.getId())
                .requestId(m.getRequest().getId())
                .senderRole(m.getSenderRole())
                .senderName(sender.getFullName())
                .content(m.getContent())
                .attachmentName(m.getAttachmentName())
                .attachmentSize(m.getAttachmentSize())
                .createdAt(m.getCreatedAt())
                .build();
    }
}