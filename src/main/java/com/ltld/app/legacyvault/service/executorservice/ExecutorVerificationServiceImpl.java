package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.CreateDeathVerificationRequest;
import com.ltld.app.legacyvault.dto.executordto.DeathVerificationResponse;
import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.VerificationRequestSubmittedEvent;
import com.ltld.app.legacyvault.exception.DmsNotTriggeredException;
import com.ltld.app.legacyvault.exception.DuplicateVerificationRequestException;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.exception.InvalidVerificationDocumentException;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
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

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorVerificationServiceImpl implements ExecutorVerificationService {

    static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<LegalVerificationStatus> OPEN_REQUEST_STATUSES =
            EnumSet.of(LegalVerificationStatus.PENDING, LegalVerificationStatus.APPROVED);

    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final DmsConfigRepository dmsConfigRepository;
    private final LegalVerificationRequestRepository legalVerificationRequestRepository;
    private final VerificationRequestVaultRepository verificationRequestVaultRepository;
    private final VaultRepository vaultRepository;
    private final AuditLogService auditLogService;
    private final CryptoService cryptoService;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DeathVerificationResponse submit(UUID assignmentId, String executorLogin,
                                            MultipartFile file, CreateDeathVerificationRequest request) {
        if (request == null || request.getVaultIds() == null || request.getVaultIds().isEmpty()) {
            throw new InvalidVerificationDocumentException("At least one vault ID must be selected.");
        }

        byte[] plain = readAndValidate(file);
        byte[] encryptedContent = encryptContent(plain);

        ExecutorAssignment assignment = executorAssignmentRepository.findById(assignmentId)
                .filter(a -> isSameUser(a.getExecutorUser(), executorLogin))
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        if (assignment.getStatus() != ExecutorStatus.ACTIVE) {
            throw new ExecutorNotActiveException("Access denied. Executor has not been activated yet.");
        }

        UUID ownerId = assignment.getOwner().getId();

        DmsConfig config = dmsConfigRepository.findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> new DmsNotTriggeredException("Dead Man's Switch configuration not found."));

        if (config.getStatus() != DmsStatus.TRIGGERED) {
            throw new DmsNotTriggeredException("Dead Man's Switch has not been triggered.");
        }

        List<Vault> linkedVaults = new ArrayList<>();
        for (UUID vaultId : new LinkedHashSet<>(request.getVaultIds())) {
            Vault vault = vaultRepository.findById(vaultId)
                    .orElseThrow(() -> new VaultException("Vault not found: " + vaultId, HttpStatus.NOT_FOUND));

            if (!vault.getOwner().getId().equals(ownerId)) {
                throw new VaultException("Vault not found: " + vaultId, HttpStatus.NOT_FOUND);
            }
            if (vault.getStatus() != VaultStatus.ACTIVE) {
                throw new VaultException("Vault is not active: " + vaultId, HttpStatus.CONFLICT);
            }
            linkedVaults.add(vault);
        }

        List<UUID> busyVaultIds = verificationRequestVaultRepository.findVaultIdsWithOpenRequest(
                linkedVaults.stream().map(Vault::getId).toList(), OPEN_REQUEST_STATUSES);
        if (!busyVaultIds.isEmpty()) {
            throw new DuplicateVerificationRequestException(
                    "A verification request is already in progress for vault(s): " + busyVaultIds);
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

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidVerificationDocumentException("Document file is required.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidVerificationDocumentException("File exceeds the 10MB limit.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidVerificationDocumentException("Unable to read uploaded file.");
        }

        if (bytes.length > MAX_FILE_SIZE) {
            throw new InvalidVerificationDocumentException("File exceeds the 10MB limit.");
        }

        String detectedExtension = detectExtensionByMagicBytes(bytes);
        if (detectedExtension == null) {
            throw new InvalidVerificationDocumentException("Only pdf, png, jpg files are allowed.");
        }

        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(detectedExtension) && !(detectedExtension.equals(".jpg") && fileName.endsWith(".jpeg"))) {
            throw new InvalidVerificationDocumentException("File extension does not match actual file content.");
        }

        return bytes;
    }

    private static String detectExtensionByMagicBytes(byte[] b) {
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return ".pdf";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return ".png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        return null;
    }

    private static boolean isSameUser(User user, String login) {
        if (user == null || login == null) {
            return false;
        }
        return login.equalsIgnoreCase(user.getEmail()) || login.equals(String.valueOf(user.getId()));
    }
}
