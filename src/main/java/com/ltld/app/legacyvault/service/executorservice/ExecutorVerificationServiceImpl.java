package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.CreateDeathVerificationRequest;
import com.ltld.app.legacyvault.dto.executordto.DeathVerificationResponse;
import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.DmsNotTriggeredException;
import com.ltld.app.legacyvault.exception.DuplicateVerificationRequestException;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.exception.InvalidVerificationDocumentException;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private static final Set<LegalVerificationStatus> ACTIVE_REQUEST_STATUSES = Set.of(
            LegalVerificationStatus.PENDING,
            LegalVerificationStatus.APPROVED,
            LegalVerificationStatus.SIGNED
    );

    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final DmsConfigRepository dmsConfigRepository;
    private final LegalVerificationRequestRepository legalVerificationRequestRepository;
    private final VerificationRequestVaultRepository verificationRequestVaultRepository;
    private final VaultRepository vaultRepository;
    private final AuditLogRepository auditLogRepository;
    private final CryptoService cryptoService;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DeathVerificationResponse submit(UUID assignmentId, String executorLogin,
                                            MultipartFile file, CreateDeathVerificationRequest request) {
        if (request == null || request.getVaultIds() == null || request.getVaultIds().isEmpty()) {
            throw new InvalidVerificationDocumentException("At least one vault ID must be selected.");
        }

        // Validate uploaded document upfront before taking database locks (H1)
        byte[] plain = readAndValidate(file);

        ExecutorAssignment assignment = executorAssignmentRepository.findById(assignmentId)
                .filter(a -> isSameUser(a.getExecutorUser(), executorLogin))
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        if (assignment.getStatus() != ExecutorStatus.ACTIVE) {
            throw new ExecutorNotActiveException("Access denied. Executor has not been activated yet.");
        }

        UUID ownerId = assignment.getOwner().getId();

        // Lock the owner's DMS config to serialize concurrent submissions across primary and backup executors
        DmsConfig config = dmsConfigRepository.findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> new DmsNotTriggeredException("Dead Man's Switch configuration not found."));

        if (config.getStatus() != DmsStatus.TRIGGERED) {
            throw new DmsNotTriggeredException("Dead Man's Switch has not been triggered.");
        }

        // Prevent duplicate submissions if a non-rejected request already exists for this owner
        if (legalVerificationRequestRepository.existsByOwnerIdAndStatusIn(ownerId, ACTIVE_REQUEST_STATUSES)) {
            throw new DuplicateVerificationRequestException(
                    "An active or pending verification request already exists for this owner.");
        }

        // Validate that all requested vaults belong to the owner and are in ACTIVE status
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

        String storedPath = null;
        try {
            byte[] encryptedContent = cryptoService.encryptBytes(plain);
            storedPath = fileStorageService.storeFile(file, encryptedContent);
            final String finalStoredPath = storedPath;

            // Register cleanup on transaction rollback (H1)
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

            String encryptedPath = cryptoService.encrypt(storedPath);

            String notes = request.getNotes();
            // Use saveAndFlush so @CreationTimestamp createdAt is populated immediately (H2)
            LegalVerificationRequest savedRequest = legalVerificationRequestRepository.saveAndFlush(
                    LegalVerificationRequest.builder()
                            .owner(assignment.getOwner())
                            .executor(assignment.getExecutorUser())
                            .status(LegalVerificationStatus.PENDING)
                            .deathCertificateFileUrlEncrypted(encryptedPath)
                            .notes(notes == null || notes.isBlank() ? null : notes.trim())
                            .build());

            // Link selected vaults to the verification request
            List<VerificationRequestVault> requestVaults = linkedVaults.stream()
                    .map(vault -> VerificationRequestVault.builder()
                            .request(savedRequest)
                            .vault(vault)
                            .build())
                    .toList();
            verificationRequestVaultRepository.saveAll(requestVaults);

            auditLogRepository.save(AuditLog.builder()
                    .actor(assignment.getExecutorUser())
                    .action(AuditAction.LEGAL_VERIFICATION_REQUESTED)
                    .entityType("LegalVerificationRequest")
                    .entityId(savedRequest.getId())
                    .metadata(Map.of(
                            "ownerId", ownerId.toString(),
                            "assignmentId", assignment.getId().toString(),
                            "executorType", assignment.getExecutorType().name(),
                            "vaultCount", linkedVaults.size()))
                    .build());

            return DeathVerificationResponse.builder()
                    .id(savedRequest.getId())
                    .assignmentId(assignment.getId())
                    .status(savedRequest.getStatus())
                    .vaultIds(linkedVaults.stream().map(Vault::getId).toList())
                    .notes(savedRequest.getNotes())
                    .createdAt(savedRequest.getCreatedAt())
                    .build();

        } catch (Exception e) {
            // Clean up stored file: if transaction synchronization is active, cleanup is handled
            // on rollback by afterCompletion. Only delete here if synchronization is not active
            // (e.g. running outside of a transaction context or in unit tests).
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
