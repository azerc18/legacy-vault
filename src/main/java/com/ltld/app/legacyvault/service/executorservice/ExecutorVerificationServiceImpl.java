package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.CreateDeathVerificationRequest;
import com.ltld.app.legacyvault.dto.executordto.DeathVerificationResponse;
import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.VerificationRequestSubmittedEvent;
import com.ltld.app.legacyvault.exception.DmsNotTriggeredException;
import com.ltld.app.legacyvault.exception.DuplicateVerificationRequestException;
import com.ltld.app.legacyvault.exception.InvalidVerificationDocumentException;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.dmsservice.DmsActivationProcessor;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorVerificationServiceImpl implements ExecutorVerificationService {

    private static final Set<LegalVerificationStatus> OPEN_REQUEST_STATUSES =
            EnumSet.of(LegalVerificationStatus.PENDING, LegalVerificationStatus.APPROVED);

    private final ExecutorAccessGuard executorAccessGuard;
    private final DmsConfigRepository dmsConfigRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final LegalVerificationRequestRepository legalVerificationRequestRepository;
    private final VerificationRequestVaultRepository verificationRequestVaultRepository;
    private final VaultRepository vaultRepository;
    private final AuditLogService auditLogService;
    private final CryptoService cryptoService;
    private final FileStorageService fileStorageService;
    private final UploadedDocumentValidator uploadedDocumentValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DeathVerificationResponse submit(UUID assignmentId, String executorLogin,
                                            MultipartFile file, CreateDeathVerificationRequest request) {
        if (request == null || request.getVaultIds() == null || request.getVaultIds().isEmpty()) {
            throw new InvalidVerificationDocumentException("Cần chọn ít nhất một Vault.");
        }

        byte[] plain = uploadedDocumentValidator.readAndValidate(file);
        byte[] encryptedContent = encryptContent(plain);

        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);

        UUID ownerId = assignment.getOwner().getId();

        DmsConfig config = dmsConfigRepository.findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> new DmsNotTriggeredException("Không tìm thấy cấu hình Dead Man's Switch."));

        if (config.getStatus() != DmsStatus.TRIGGERED) {
            throw new DmsNotTriggeredException("Dead Man's Switch chưa được kích hoạt.");
        }

        List<Vault> linkedVaults = new ArrayList<>();
        for (UUID vaultId : new LinkedHashSet<>(request.getVaultIds())) {
            Vault vault = vaultRepository.findById(vaultId)
                    .orElseThrow(() -> new VaultException("Không tìm thấy Vault: " + vaultId, HttpStatus.NOT_FOUND));

            if (!vault.getOwner().getId().equals(ownerId)) {
                throw new VaultException("Không tìm thấy Vault: " + vaultId, HttpStatus.NOT_FOUND);
            }
            if (vault.getStatus() != VaultStatus.ACTIVE) {
                throw new VaultException("Vault không còn hoạt động: " + vaultId, HttpStatus.CONFLICT);
            }
            linkedVaults.add(vault);
        }

        List<UUID> busyVaultIds = verificationRequestVaultRepository.findVaultIdsWithOpenRequest(
                linkedVaults.stream().map(Vault::getId).toList(), OPEN_REQUEST_STATUSES);
        if (!busyVaultIds.isEmpty()) {
            throw new DuplicateVerificationRequestException(
                    "Đã có yêu cầu xác minh đang được xử lý cho Vault: " + busyVaultIds);
        }

        // B2 (F1): nộp hồ sơ = đã phản hồi, nên BR-03 không thu hồi Primary làm hồ sơ bị kẹt.
        // Flush ngay để xung đột @Version với lượt leo thang nổ ra trước khi lưu file.
        boolean acknowledgedNow = assignment.getAcknowledgedAt() == null;
        if (acknowledgedNow) {
            assignment.setAcknowledgedAt(LocalDateTime.now());
            executorAssignmentRepository.saveAndFlush(assignment);
        }

        String storedPath = null;
        try {
            storedPath = fileStorageService.storeFile(file, encryptedContent);
            final String finalStoredPath = storedPath;

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            try {
                                fileStorageService.deleteFile(finalStoredPath);
                            } catch (Exception ex) {
                                log.error("Failed to delete orphaned file after rollback: {}", finalStoredPath, ex);
                            }
                        }
                    }
                });
            }

            String notes = request.getNotes();
            LegalVerificationRequest savedRequest = legalVerificationRequestRepository.saveAndFlush(
                    LegalVerificationRequest.builder()
                            .owner(assignment.getOwner())
                            .executor(assignment.getExecutorUser())
                            .status(LegalVerificationStatus.PENDING)
                            .deathCertificateFileUrlEncrypted(storedPath)
                            .notes(notes == null || notes.isBlank() ? null : notes.trim())
                            .build());

            List<VerificationRequestVault> requestVaults = linkedVaults.stream()
                    .map(vault -> VerificationRequestVault.builder()
                            .request(savedRequest)
                            .vault(vault)
                            .build())
                    .toList();
            verificationRequestVaultRepository.saveAll(requestVaults);

            User executor = assignment.getExecutorUser();
            String auditDetail = "assignmentId=" + assignment.getId()
                    + ", executorType=" + assignment.getExecutorType();
            for (Vault vault : linkedVaults) {
                auditLogService.log(AuditAction.LEGAL_VERIFICATION_REQUESTED, AuditResult.SUCCESS,
                        executor.getId(), executor.getEmail(), vault.getId(),
                        "LegalVerificationRequest", savedRequest.getId().toString(), auditDetail);
            }
            if (acknowledgedNow) {
                // vaultId = null: giống FR-07, xác nhận gắn với Owner chứ không với một Vault cụ thể.
                auditLogService.log(AuditAction.EXECUTOR_ACKNOWLEDGED, AuditResult.SUCCESS,
                        executor.getId(), executor.getEmail(), null,
                        DmsActivationProcessor.ENTITY_EXECUTOR_ASSIGNMENT, assignment.getId().toString(),
                        "ownerId=" + ownerId + ", executorType=" + assignment.getExecutorType()
                                + ", via=VERIFICATION_SUBMIT");
            }

            eventPublisher.publishEvent(new VerificationRequestSubmittedEvent(
                    savedRequest.getId(), ownerId, executor.getId()));

            return DeathVerificationResponse.builder()
                    .id(savedRequest.getId())
                    .assignmentId(assignment.getId())
                    .status(savedRequest.getStatus())
                    .vaultIds(linkedVaults.stream().map(Vault::getId).toList())
                    .notes(savedRequest.getNotes())
                    .createdAt(savedRequest.getCreatedAt())
                    .build();

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
            throw new IllegalStateException("Failed to process verification request", e);
        }
    }

    private byte[] encryptContent(byte[] plain) {
        try {
            return cryptoService.encryptBytes(plain);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process verification request", e);
        }
    }
}
