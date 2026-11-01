package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.VerificationExchangeMessage;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ExchangeSenderRole;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.event.VerificationMessagePostedEvent;
import com.ltld.app.legacyvault.exception.InvalidVerificationDocumentException;
import com.ltld.app.legacyvault.exception.VerificationRequestClosedException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.exception.StorageUnavailableException;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VerificationExchangeMessageRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationExchangeServiceImpl implements VerificationExchangeService {

    private static final Set<LegalVerificationStatus> OPEN_STATUSES =
            EnumSet.of(LegalVerificationStatus.PENDING, LegalVerificationStatus.APPROVED);
    private static final int MAX_ATTACHMENT_NAME_LENGTH = 255;
    private static final int MAX_PAGE_SIZE = 100;

    private final ExecutorAccessGuard executorAccessGuard;
    private final LegalVerificationRequestRepository legalVerificationRequestRepository;
    private final VerificationExchangeMessageRepository verificationExchangeMessageRepository;
    private final UploadedDocumentValidator uploadedDocumentValidator;
    private final CryptoService cryptoService;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public VerificationMessageResponse send(UUID assignmentId, UUID requestId, String executorLogin,
                                            MultipartFile file, SendVerificationMessageRequest request) {
        String content = normalize(request == null ? null : request.getContent());
        boolean hasFile = file != null && !file.isEmpty();
        if (content == null && !hasFile) {
            throw new InvalidVerificationDocumentException("Message content or attachment is required.");
        }

        byte[] encryptedContent = null;
        if (hasFile) {
            encryptedContent = encrypt(uploadedDocumentValidator.readAndValidate(file));
        }

        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);
        LegalVerificationRequest verificationRequest = findOwnRequest(assignment, requestId);
        if (!OPEN_STATUSES.contains(verificationRequest.getStatus())) {
            throw new VerificationRequestClosedException("This verification request is closed.");
        }

        User executor = assignment.getExecutorUser();
        String storedPath = null;
        try {
            if (hasFile) {
                storedPath = fileStorageService.storeFile(file, encryptedContent);
                registerCleanupOnRollback(storedPath);
            }

            VerificationExchangeMessage saved = verificationExchangeMessageRepository.saveAndFlush(
                    VerificationExchangeMessage.builder()
                            .request(verificationRequest)
                            .sender(executor)
                            .senderRole(ExchangeSenderRole.EXECUTOR)
                            .content(content)
                            .attachmentPath(storedPath)
                            .attachmentName(hasFile ? sanitizeFileName(file.getOriginalFilename()) : null)
                            .attachmentSize(hasFile ? file.getSize() : null)
                            .build());

            auditLogService.log(AuditAction.VERIFICATION_MESSAGE_SENT, AuditResult.SUCCESS,
                    executor.getId(), executor.getEmail(),
                    "LegalVerificationRequest", requestId.toString(),
                    "assignmentId=" + assignment.getId() + ", hasAttachment=" + hasFile);

            User verifier = verificationRequest.getVerifier();
            eventPublisher.publishEvent(new VerificationMessagePostedEvent(
                    requestId, assignment.getOwner().getId(), executor.getId(),
                    verifier == null ? null : verifier.getId()));

            return toResponse(saved, executor);
        } catch (Exception e) {
            if (storedPath != null && !TransactionSynchronizationManager.isSynchronizationActive()) {
                try {
                    fileStorageService.deleteFile(storedPath);
                } catch (Exception ex) {
                    log.error("Failed to delete orphaned file after failure: {}", storedPath, ex);
                }
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to send verification message", e);
        }
    }

    @Override
    @Transactional
    public Page<VerificationMessageResponse> history(UUID assignmentId, UUID requestId, String executorLogin,
                                                     int page, int size) {
        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);
        findOwnRequest(assignment, requestId);

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<VerificationExchangeMessage> messages = verificationExchangeMessageRepository.findHistoryPage(requestId, pageable);
        // FR-11: chỉ đánh dấu đã đọc tới tin mới nhất Executor thực sự nhận được (trang 0 = mới nhất), không dùng now():
        // tin của Verifier tạo/commit sau lần truy vấn vẫn được tính là chưa đọc.
        if (pageable.getPageNumber() == 0 && !messages.isEmpty()) {
            legalVerificationRequestRepository.markExecutorRead(requestId, messages.getContent().get(0).getCreatedAt());
        }
        return messages.map(message -> toResponse(message, message.getSender()));
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationDocumentDto getAttachment(UUID assignmentId, UUID requestId, UUID messageId,
                                                 String executorLogin) {
        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);
        findOwnRequest(assignment, requestId);

        VerificationExchangeMessage message = verificationExchangeMessageRepository
                .findByIdAndRequestId(messageId, requestId)
                .filter(m -> m.getAttachmentPath() != null)
                .orElseThrow(() -> new VerificationRequestNotFoundException("Attachment not found."));

        // Cùng cách giải mã với phía Verifier (VerifierMessageServiceImpl.getAttachment).
        byte[] plain;
        try {
            plain = cryptoService.decryptBytes(fileStorageService.readFile(message.getAttachmentPath()));
        } catch (StorageUnavailableException e) {
            throw e; // H1: storage mất kết nối -> 503 STORAGE_UNAVAILABLE, không phải lỗi giải mã
        } catch (Exception e) {
            log.error("Attachment read/decrypt failed for message {}", messageId, e);
            throw new IllegalStateException("Could not read the attachment. Please try again later.");
        }

        User executor = assignment.getExecutorUser();
        auditLogService.log(AuditAction.VERIFICATION_ATTACHMENT_VIEWED, AuditResult.SUCCESS,
                executor.getId(), executor.getEmail(), "VerificationExchangeMessage", messageId.toString(),
                "assignmentId=" + assignment.getId() + ", requestId=" + requestId);

        return VerificationDocumentDto.builder()
                .content(plain)
                .contentType(UploadedDocumentValidator.detectContentType(plain))
                .fileName(message.getAttachmentName() != null ? message.getAttachmentName() : "attachment")
                .build();
    }

    private LegalVerificationRequest findOwnRequest(ExecutorAssignment assignment, UUID requestId) {
        return legalVerificationRequestRepository.findById(requestId)
                .filter(r -> r.getOwner().getId().equals(assignment.getOwner().getId())
                        && r.getExecutor().getId().equals(assignment.getExecutorUser().getId()))
                .orElseThrow(() -> new VerificationRequestNotFoundException("Verification request not found."));
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
        String name = originalName.substring(Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\')) + 1)
                .replaceAll("\\p{Cntrl}", "")
                .trim();
        if (name.isEmpty()) {
            return null;
        }
        return name.length() > MAX_ATTACHMENT_NAME_LENGTH ? name.substring(0, MAX_ATTACHMENT_NAME_LENGTH) : name;
    }

    private static VerificationMessageResponse toResponse(VerificationExchangeMessage message, User sender) {
        return VerificationMessageResponse.builder()
                .id(message.getId())
                .requestId(message.getRequest().getId())
                .senderRole(message.getSenderRole())
                .senderName(sender.getFullName())
                .content(message.getContent())
                .attachmentName(message.getAttachmentName())
                .attachmentSize(message.getAttachmentSize())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
