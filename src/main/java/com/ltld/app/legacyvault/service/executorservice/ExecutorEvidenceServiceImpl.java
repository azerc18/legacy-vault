package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorTaskResponse;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.ExecutorEvidenceFile;
import com.ltld.app.legacyvault.entity.ExecutorTask;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ExecutorTaskStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.ExecutorEvidenceFileRepository;
import com.ltld.app.legacyvault.repository.ExecutorTaskRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import com.ltld.app.legacyvault.utility.AfterCommit;
import com.ltld.app.legacyvault.utility.Sha256;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * C.4: Executor đính kèm tệp bằng chứng (ảnh chụp màn hình, PDF xác nhận của nền tảng...). Tệp được mã hóa như tài liệu
 * pháp lý (CryptoService + FileStorageService), hash SHA-256 của nội dung gốc được lưu để Beneficiary đối chiếu.
 * Không dùng bảng legal_documents để tệp bằng chứng không lẫn vào danh sách tài liệu của Owner / Beneficiary / dossier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorEvidenceServiceImpl implements ExecutorEvidenceService {

    private static final String TASK_NOT_FOUND = "Không tìm thấy việc cần xử lý.";

    private final ExecutorAccessGuard executorAccessGuard;
    private final ExecutorTaskRepository executorTaskRepository;
    private final ExecutorEvidenceFileRepository evidenceRepository;
    private final CryptoService cryptoService;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final UploadedDocumentValidator uploadedDocumentValidator;

    @Override
    @Transactional
    public ExecutorTaskResponse upload(UUID assignmentId, UUID taskId, MultipartFile file, String executorLogin)
            throws Exception {
        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);
        ExecutorTask task = loadTask(assignment, taskId, true);
        if (task.getStatus() == ExecutorTaskStatus.DONE) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Việc đã báo hoàn tất, hãy chuyển về \"Đang làm\" trước khi thay bằng chứng.");
        }

        byte[] plain = uploadedDocumentValidator.readAndValidate(file);
        String sha256 = Sha256.hex(plain);
        byte[] encrypted = cryptoService.encryptBytes(plain);
        String newUrl = fileStorageService.storeFile(file, encrypted);
        // Giao dịch lỗi / rollback thì dọn tệp vừa ghi để không mồ côi
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        deleteQuietly(newUrl);
                    }
                }
            });
        }

        LocalDateTime now = LocalDateTime.now();
        UUID executorUserId = assignment.getExecutorUser().getId();
        ExecutorEvidenceFile evidence = evidenceRepository.findByTaskId(task.getId()).orElse(null);
        String oldUrl = null;
        if (evidence == null) {
            evidence = ExecutorEvidenceFile.builder().task(task).vault(task.getVault()).build();
        } else {
            oldUrl = evidence.getFileUrlEncrypted();
        }
        String fileName = safeName(file.getOriginalFilename());
        evidence.setFileName(fileName);
        evidence.setFileUrlEncrypted(newUrl);
        evidence.setSizeBytes((long) encrypted.length);
        evidence.setSha256(sha256);
        evidence.setUploadedBy(executorUserId);
        evidence.setUploadedAt(now);
        evidence = evidenceRepository.saveAndFlush(evidence);

        task.setEvidenceDocumentId(evidence.getId());
        task.setEvidenceFileName(fileName);
        task.setEvidenceSha256(sha256);
        task.setEvidenceUploadedAt(now);
        ExecutorTask saved = executorTaskRepository.saveAndFlush(task);

        if (oldUrl != null && !oldUrl.equals(newUrl)) {
            String toDelete = oldUrl;
            AfterCommit.run(() -> deleteQuietly(toDelete));
        }
        // Chỉ ghi hash, không ghi nội dung
        auditLogService.log(AuditAction.EXECUTOR_EVIDENCE_ADDED, AuditResult.SUCCESS, executorUserId,
                assignment.getExecutorUser().getEmail(), task.getVault().getId(), "ExecutorTask",
                task.getId().toString(), "file sha256=" + sha256);
        return ExecutorTaskServiceImpl.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDownloadResponse download(UUID assignmentId, UUID taskId, String executorLogin) throws Exception {
        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);
        ExecutorTask task = loadTask(assignment, taskId, false);
        ExecutorEvidenceFile evidence = evidenceRepository.findByTaskId(task.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Việc này chưa có tệp bằng chứng."));

        byte[] decrypted = cryptoService.decryptBytes(fileStorageService.readFile(evidence.getFileUrlEncrypted()));
        auditLogService.log(AuditAction.DOCUMENT_DOWNLOADED, AuditResult.SUCCESS,
                assignment.getExecutorUser().getId(), assignment.getExecutorUser().getEmail(),
                task.getVault().getId(), "ExecutorEvidence", evidence.getId().toString(),
                "Executor downloaded evidence file");
        return DocumentDownloadResponse.builder()
                .fileName(evidence.getFileName())
                .contentType(UploadedDocumentValidator.detectContentType(decrypted))
                .content(decrypted)
                .build();
    }

    private ExecutorTask loadTask(ExecutorAssignment assignment, UUID taskId, boolean forUpdate) {
        Optional<ExecutorTask> found = forUpdate
                ? executorTaskRepository.findByIdForUpdate(taskId)
                : executorTaskRepository.findById(taskId);
        ExecutorTask task = found
                .filter(t -> t.getVault().getOwner().getId().equals(assignment.getOwner().getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, TASK_NOT_FOUND));
        if (task.getVault().getStatus() == VaultStatus.ARCHIVED_LOCKED) {
            throw new BusinessException(ErrorCode.VAULT_STATE_CONFLICT,
                    "Vault đã hết hạn bàn giao, không thể cập nhật checklist.");
        }
        return task;
    }

    // Bỏ phần đường dẫn mà trình duyệt cũ có thể gửi kèm; tối đa 255 ký tự
    private static String safeName(String original) {
        String name = original == null ? "" : original.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1).trim();
        }
        if (name.isEmpty()) {
            name = "evidence";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private void deleteQuietly(String path) {
        try {
            fileStorageService.deleteFile(path);
        } catch (RuntimeException e) {
            log.warn("C.4: could not delete evidence file {}: {}", path, e.getMessage());
        }
    }
}
