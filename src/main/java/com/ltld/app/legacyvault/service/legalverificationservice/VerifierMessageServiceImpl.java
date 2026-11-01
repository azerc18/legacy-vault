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
import com.ltld.app.legacyvault.exception.VerificationRequestClosedException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.exception.StorageUnavailableException;
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

    private final VerifierAccessGuard verifierAccessGuard;
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
        LegalVerificationRequest request = verifierAccessGuard.requireReadable(requestId, verifierId);
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
            throw new InvalidVerificationDocumentException("Please enter a message or attach a file.");
        }

        // n2: kiểm quyền trước, người không có quyền không làm server tốn công đọc/mã hóa file
        LegalVerificationRequest request = verifierAccessGuard.requireReadable(requestId, verifierId);
        if (!OPEN_STATUSES.contains(request.getStatus())) {
            throw new VerificationRequestClosedException("This verification request is closed.");
        }

        byte[] encrypted = null;
        if (hasFile) {
            encrypted = encrypt(uploadedDocumentValidator.readAndValidate(file));
        }

        User verifier = userRepository.findById(verifierId)
                .orElseThrow(() -> new IllegalStateException("Legal Verifier not found."));

        String storedPath = null;
        if (hasFile) {
            try {
                storedPath = fileStorageService.storeFile(file, encrypted);
            } catch (RuntimeException e) {
                throw e; // giữ nguyên kiểu, để handler map đúng mã lỗi
            } catch (Exception e) {
                throw new IllegalStateException("Could not save the attachment. Please try again later.", e);
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
        verifierAccessGuard.requireReadable(requestId, verifierId);

        VerificationExchangeMessage message = messageRepository.findByIdAndRequestId(messageId, requestId)
                .filter(m -> m.getAttachmentPath() != null)
                .orElseThrow(() -> new VerificationRequestNotFoundException("Attachment not found."));

        byte[] plain;
        try {
            plain = cryptoService.decryptBytes(fileStorageService.readFile(message.getAttachmentPath()));
        } catch (StorageUnavailableException e) {
            throw e; // H1: storage mất kết nối -> 503 STORAGE_UNAVAILABLE, không phải lỗi giải mã
        } catch (Exception e) {
            log.error("Attachment read/decrypt failed for message {}", messageId, e);
            throw new IllegalStateException("Could not read the attachment. Please try again later.");
        }

        String email = userRepository.findById(verifierId).map(User::getEmail).orElse(null);
        auditLogService.log(AuditAction.VERIFICATION_ATTACHMENT_VIEWED, AuditResult.SUCCESS,
                verifierId, email, "VerificationExchangeMessage", messageId.toString(), null);

        String name = message.getAttachmentName() != null ? message.getAttachmentName() : "attachment";
        return VerificationDocumentDto.builder()
                .content(plain)
                .contentType(UploadedDocumentValidator.detectContentType(plain))
                .fileName(name)
                .build();
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
            throw new IllegalStateException("Could not send the message. Please try again later.", e);
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
