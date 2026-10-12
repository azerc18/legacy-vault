package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.IdentityVerification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.VerificationExchangeMessage;
import com.ltld.app.legacyvault.enums.ExchangeSenderRole;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.exception.InvalidAttachmentException;
import com.ltld.app.legacyvault.exception.VerificationNotFoundException;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationExchangeMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationExchangeServiceImpl implements VerificationExchangeService {

    private static final long MAX_ATTACHMENT_SIZE_BYTES = 10L * 1024 * 1024; // 10MB giới hạn dung lượng tệp

    private final VerificationExchangeMessageRepository messageRepository;
    private final IdentityVerificationRepository verificationRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public VerificationMessageResponse sendExecutorMessage(
            UUID verificationId, SendVerificationMessageRequest request, String executorLogin) {

        IdentityVerification verification = findVerificationAndVerifyExecutorAccess(verificationId, executorLogin);
        User executorUser = findUserByLogin(executorLogin)
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        validateMessageRequest(request);

        VerificationExchangeMessage message = VerificationExchangeMessage.builder()
                .verification(verification)
                .sender(executorUser)
                .senderRole(ExchangeSenderRole.EXECUTOR)
                .messageContent(request.getMessageContent() != null ? request.getMessageContent().trim() : null)
                .attachmentUrl(request.getAttachmentUrl() != null ? request.getAttachmentUrl().trim() : null)
                .attachmentName(request.getAttachmentName() != null ? request.getAttachmentName().trim() : null)
                .attachmentSize(request.getAttachmentSize())
                .build();

        VerificationExchangeMessage saved = messageRepository.save(message);

        return mapToResponse(saved);
    }

    @Override
    public List<VerificationMessageResponse> getExchangeMessages(UUID verificationId, String executorLogin) {
        findVerificationAndVerifyExecutorAccess(verificationId, executorLogin);

        return messageRepository.findAllByVerificationIdOrderByCreatedAtAsc(verificationId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private IdentityVerification findVerificationAndVerifyExecutorAccess(UUID verificationId, String executorLogin) {
        IdentityVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new VerificationNotFoundException("Identity verification request not found."));

        User user = findUserByLogin(executorLogin)
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        UUID ownerId = verification.getVault().getOwner().getId();

        List<ExecutorAssignment> assignments = executorAssignmentRepository.findAllByExecutorUserId(user.getId());
        ExecutorAssignment activeAssignment = assignments.stream()
                .filter(a -> a.getOwner().getId().equals(ownerId))
                .findFirst()
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException("Executor not assigned to this vault."));

        if (activeAssignment.getStatus() != ExecutorStatus.ACTIVE) {
            throw new ExecutorNotActiveException("Access denied. Executor has not been activated yet.");
        }

        return verification;
    }

    private void validateMessageRequest(SendVerificationMessageRequest request) {
        boolean hasContent = request.getMessageContent() != null && !request.getMessageContent().trim().isEmpty();
        boolean hasAttachment = request.getAttachmentUrl() != null && !request.getAttachmentUrl().trim().isEmpty();

        if (!hasContent && !hasAttachment) {
            throw new InvalidAttachmentException("Message content or attachment must not be empty.");
        }

        if (hasAttachment) {
            String url = request.getAttachmentUrl().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new InvalidAttachmentException("Invalid attachment URL.");
            }
        }

        if (request.getAttachmentSize() != null) {
            if (request.getAttachmentSize() < 0) {
                throw new InvalidAttachmentException("Invalid attachment size.");
            }
            if (request.getAttachmentSize() > MAX_ATTACHMENT_SIZE_BYTES) {
                throw new InvalidAttachmentException("Attachment size exceeds the maximum allowed limit (10MB).");
            }
        }
    }

    private VerificationMessageResponse mapToResponse(VerificationExchangeMessage entity) {
        return VerificationMessageResponse.builder()
                .id(entity.getId())
                .verificationId(entity.getVerification().getId())
                .senderId(entity.getSender().getId())
                .senderName(entity.getSender().getFullName())
                .senderEmail(entity.getSender().getEmail())
                .senderRole(entity.getSenderRole())
                .messageContent(entity.getMessageContent())
                .attachmentUrl(entity.getAttachmentUrl())
                .attachmentName(entity.getAttachmentName())
                .attachmentSize(entity.getAttachmentSize())
                .createdAt(entity.getCreatedAt())
                .build();
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
