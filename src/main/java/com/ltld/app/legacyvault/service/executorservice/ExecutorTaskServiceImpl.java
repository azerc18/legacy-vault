package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.DisputeExecutorTaskRequest;
import com.ltld.app.legacyvault.dto.executordto.ExecutorTaskResponse;
import com.ltld.app.legacyvault.dto.executordto.UpdateExecutorTaskRequest;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.ExecutorTask;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.ExecutorTaskStatus;
import com.ltld.app.legacyvault.enums.ExecutorTaskWish;
import com.ltld.app.legacyvault.enums.TaskConfirmationStatus;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.ExecutorTaskCompletedEvent;
import com.ltld.app.legacyvault.event.ExecutorTaskDisputedEvent;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.ExecutorTaskRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ExecutorTaskServiceImpl implements ExecutorTaskService {

    private static final String TASK_NOT_FOUND = "Không tìm thấy việc cần xử lý.";
    private static final String VAULT_NOT_FOUND = "Không tìm thấy Vault.";

    // Trang hỗ trợ chính thức cho tài khoản của người đã mất; nền tảng chưa có trang riêng thì dẫn về trung tâm trợ giúp
    private static final Map<String, String> HELP_URLS = Map.ofEntries(
            Map.entry("Facebook", "https://www.facebook.com/help/contact/228813257197480"),
            Map.entry("Instagram", "https://help.instagram.com/264154560391256"),
            Map.entry("TikTok", "https://support.tiktok.com"),
            Map.entry("X", "https://help.x.com/en/rules-and-policies/contact-x-about-a-deceased-family-members-account"),
            Map.entry("LinkedIn", "https://www.linkedin.com/help/linkedin"),
            Map.entry("YouTube", "https://support.google.com/accounts/troubleshooter/6357590"),
            Map.entry("Gmail", "https://support.google.com/accounts/troubleshooter/6357590"),
            Map.entry("Outlook", "https://support.microsoft.com/account-billing"),
            Map.entry("Yahoo", "https://help.yahoo.com"),
            Map.entry("Apple ID", "https://support.apple.com/en-us/102431"),
            Map.entry("MoMo", "https://momo.vn/hoi-dap"),
            Map.entry("ZaloPay", "https://zalopay.vn"),
            Map.entry("ViettelPay", "https://viettelmoney.vn"),
            Map.entry("PayPal", "https://www.paypal.com/smarthelp/contact-us"));
    // Facebook tách riêng biểu mẫu chuyển sang chế độ tưởng niệm
    private static final String FACEBOOK_MEMORIALIZE_URL = "https://www.facebook.com/help/contact/234739086860192";

    private final ExecutorAccessGuard executorAccessGuard;
    private final ExecutorTaskRepository executorTaskRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final VaultRepository vaultRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final AssetAllocationRepository assetAllocationRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    // Mỗi bước chạy transaction riêng (REQUIRES_NEW): task vừa sinh đã commit thì bước đọc sau mới thấy
    // (MySQL REPEATABLE READ), và lỗi trùng của một task không làm hỏng cả request.
    private final TransactionTemplate newTx;

    // C.6: số ngày Beneficiary được phản đối sau khi Executor báo DONE. Field (không qua constructor)
    // để unit test với @InjectMocks vẫn dùng được giá trị mặc định.
    @Value("${app.executor.task.confirm-days:7}")
    private int confirmDays = 7;

    public ExecutorTaskServiceImpl(ExecutorAccessGuard executorAccessGuard,
                                   ExecutorTaskRepository executorTaskRepository,
                                   DigitalAssetRepository digitalAssetRepository,
                                   VaultRepository vaultRepository,
                                   VaultBeneficiaryRepository vaultBeneficiaryRepository,
                                   AssetAllocationRepository assetAllocationRepository,
                                   AuditLogService auditLogService,
                                   ApplicationEventPublisher eventPublisher,
                                   PlatformTransactionManager transactionManager) {
        this.executorAccessGuard = executorAccessGuard;
        this.executorTaskRepository = executorTaskRepository;
        this.digitalAssetRepository = digitalAssetRepository;
        this.vaultRepository = vaultRepository;
        this.vaultBeneficiaryRepository = vaultBeneficiaryRepository;
        this.assetAllocationRepository = assetAllocationRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Không @Transactional: mỗi task được ghi trong transaction riêng (insertIgnoringDuplicate)
    @Override
    public int generateForVaults(Collection<UUID> vaultIds) {
        if (vaultIds == null || vaultIds.isEmpty()) {
            return 0;
        }
        List<DigitalAsset> assets = digitalAssetRepository.findByVaultIdInAndStatus(vaultIds, AssetStatus.ACTIVE)
                .stream()
                .filter(a -> a.getTransferMechanism() == TransferMechanism.EXECUTOR_DISPOSITION)
                .toList();
        if (assets.isEmpty()) {
            return 0;
        }
        Set<UUID> done = executorTaskRepository.findAssetIdsWithTask(assets.stream().map(DigitalAsset::getId).toList());

        int created = 0;
        for (DigitalAsset asset : assets) {
            if (done.contains(asset.getId())) {
                continue;
            }
            Map<String, String> fields = FlatJson.decode(asset.getPublicFieldsJson());
            ExecutorTaskWish wish = parseWish(fields.get("wish"));
            if (wish == null) {
                // Tài sản tạo trước N3 không có di nguyện: không đoán thay Owner
                log.warn("F5: asset {} has no valid wish, skipping executor task", asset.getId());
                continue;
            }
            boolean inserted = insertIgnoringDuplicate(ExecutorTask.builder()
                    .vault(asset.getVault())
                    .asset(asset)
                    .platform(fields.get("platform"))
                    .accountHandle(fields.get("usernameOrHandle"))
                    .wish(wish)
                    .build());
            if (inserted) {
                created++;
            }
        }
        return created;
    }

    /**
     * Ghi một task; nếu request / listener khác vừa ghi task cho cùng tài sản (unique uk_executor_task_asset)
     * thì bỏ qua, vì task đã có là đúng ý. Ví dụ: React StrictMode gửi 2 GET cùng lúc, hoặc trùng với listener mở khóa.
     */
    boolean insertIgnoringDuplicate(ExecutorTask task) {
        try {
            newTx.executeWithoutResult(status -> executorTaskRepository.saveAndFlush(task));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("F5: task for asset {} already exists, skipping", task.getAsset().getId());
            return false;
        }
    }

    // Không @Transactional: kiểm quyền, sinh task và đọc lại là 3 transaction riêng (xem newTx)
    @Override
    public List<ExecutorTaskResponse> listForExecutor(UUID assignmentId, String executorLogin) {
        List<UUID> vaultIds = newTx.execute(status -> {
            ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);
            return vaultRepository.findByOwnerIdOrderByCreatedAtDesc(assignment.getOwner().getId())
                    .stream()
                    .filter(v -> v.getStatus() != VaultStatus.ACTIVE)
                    .map(Vault::getId)
                    .toList();
        });
        if (vaultIds == null || vaultIds.isEmpty()) {
            return List.of();
        }
        // Vault mở khóa trước khi có F5 (hoặc listener lỗi) vẫn có checklist khi Executor mở ra
        generateForVaults(vaultIds);
        return newTx.execute(status -> executorTaskRepository.findByVaultIds(vaultIds).stream()
                .map(ExecutorTaskServiceImpl::toResponse)
                .toList());
    }

    @Override
    @Transactional
    public ExecutorTaskResponse update(UUID assignmentId, UUID taskId, UpdateExecutorTaskRequest request,
                                       String executorLogin) {
        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);
        // Controller đã @Valid; kiểm lại để service an toàn khi được gọi từ nơi khác
        if (request == null || request.getStatus() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Vui lòng chọn trạng thái.");
        }
        ExecutorTask task = executorTaskRepository.findByIdForUpdate(taskId)
                .filter(t -> t.getVault().getOwner().getId().equals(assignment.getOwner().getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, TASK_NOT_FOUND));
        if (task.getVault().getStatus() == VaultStatus.ARCHIVED_LOCKED) {
            throw new BusinessException(ErrorCode.VAULT_STATE_CONFLICT,
                    "Vault đã hết hạn bàn giao, không thể cập nhật checklist.");
        }

        if (task.getConfirmationStatus() == TaskConfirmationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Người thụ hưởng đã xác nhận việc này, không thể thay đổi nữa.");
        }

        // C.6: hết hạn phản đối thì coi như đã được chấp nhận (khớp autoConfirmed trong toResponse)
        if (task.getConfirmationStatus() == TaskConfirmationStatus.PENDING
                && task.getConfirmDeadlineAt() != null
                && task.getConfirmDeadlineAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Đã hết thời hạn phản đối, việc này được xem là đã xác nhận nên không thể thay đổi nữa.");
        }

        String note = request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim();
        if (note != null && note.length() > 1000) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Ghi chú tối đa 1000 ký tự.");
        }

        // C.4: DONE cần bằng chứng. Kiểm TRƯỚC khi đổi bất kỳ field nào của task,
        // để khi bị từ chối thì task giữ nguyên (trạng thái, ghi chú, mã tham chiếu).
        String evidenceRef = request.getEvidenceRef() == null || request.getEvidenceRef().isBlank()
                ? null : request.getEvidenceRef().trim();
        if (request.getStatus() == ExecutorTaskStatus.DONE
                && evidenceRef == null
                && task.getEvidenceRef() == null
                && task.getEvidenceDocumentId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Cần nhập mã tham chiếu hoặc đính kèm bằng chứng trước khi đánh dấu hoàn tất.");
        }

        if (task.getStatus() == ExecutorTaskStatus.DONE
                && request.getStatus() == ExecutorTaskStatus.DONE
                && evidenceRef != null && !evidenceRef.equals(task.getEvidenceRef())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Việc đã báo hoàn tất, hãy chuyển về \"Đang làm\" trước khi đổi bằng chứng.");
        }

        // C.6: sau khi Beneficiary phản đối, Executor phải có bằng chứng MỚI mới được hoàn tất lại
        boolean redoAfterDispute = task.getConfirmationStatus() == TaskConfirmationStatus.DISPUTED;
        if (request.getStatus() == ExecutorTaskStatus.DONE && redoAfterDispute
                && !hasFreshEvidence(task, evidenceRef)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Người thụ hưởng đã phản đối kết quả này. Hãy nhập mã tham chiếu mới hoặc tải bằng chứng mới "
                            + "trước khi hoàn tất lại.");
        }

        LocalDateTime now = LocalDateTime.now();
        ExecutorTaskStatus previous = task.getStatus();
        boolean evidenceRefChanged = evidenceRef != null && !evidenceRef.equals(task.getEvidenceRef());
        task.setStatus(request.getStatus());
        task.setNote(note);
        if (evidenceRefChanged) {
            task.setEvidenceRef(evidenceRef);
        }
        boolean newlyCompleted = false;
        if (request.getStatus() == ExecutorTaskStatus.DONE) {
            if (task.getCompletedAt() == null) {
                task.setCompletedAt(now);
            }
            // DONE mới (hoặc DONE lại sau phản đối): mở cửa sổ xác nhận / phản đối cho Beneficiary
            if (previous != ExecutorTaskStatus.DONE || redoAfterDispute) {
                task.setConfirmationStatus(TaskConfirmationStatus.PENDING);
                task.setConfirmDeadlineAt(now.plusDays(confirmDays));
                task.setConfirmedAt(null);
                task.setConfirmedBy(null);
                task.setDisputeReason(null);
                task.setDisputedAt(null);
                task.setDisputedBy(null);
                newlyCompleted = true;
            }
        } else {
            task.setCompletedAt(null);
            // Giữ nguyên lý do phản đối để Executor còn thấy; các trường xác nhận khác thì xóa
            if (task.getConfirmationStatus() != TaskConfirmationStatus.DISPUTED) {
                task.setConfirmationStatus(null);
                task.setConfirmDeadlineAt(null);
                task.setConfirmedAt(null);
                task.setConfirmedBy(null);
            }
        }
        ExecutorTask saved = executorTaskRepository.saveAndFlush(task);

        auditLogService.log(AuditAction.EXECUTOR_TASK_UPDATED, AuditResult.SUCCESS,
                assignment.getExecutorUser().getId(), assignment.getExecutorUser().getEmail(), task.getVault().getId(),
                "ExecutorTask", task.getId().toString(), previous + " -> " + request.getStatus());
        if (evidenceRefChanged) {
            auditLogService.log(AuditAction.EXECUTOR_EVIDENCE_ADDED, AuditResult.SUCCESS,
                    assignment.getExecutorUser().getId(), assignment.getExecutorUser().getEmail(),
                    task.getVault().getId(), "ExecutorTask", task.getId().toString(), "evidenceRef");
        }
        if (newlyCompleted) {
            eventPublisher.publishEvent(new ExecutorTaskCompletedEvent(saved.getId(), task.getVault().getId(),
                    task.getVault().getOwner().getId(), assignment.getExecutorUser().getId(),
                    saved.getEvidenceDocumentId() != null));
        }
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutorTaskResponse> listForBeneficiary(UUID vaultId, UUID currentUserId) {
        // Không phải người thụ hưởng của Vault, hoặc Vault chưa bàn giao: 404 để không lộ Vault có tồn tại
        Vault vault = vaultRepository.findById(vaultId)
                .filter(v -> v.getStatus() != VaultStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, VAULT_NOT_FOUND));
        VaultBeneficiary me = vaultBeneficiaryRepository.findByVaultIdAndUserId(vault.getId(), currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, VAULT_NOT_FOUND));
        // Như các API Beneficiary khác: đã từ chối / từ bỏ thì không xem nữa
        if (me.getStatus() == BeneficiaryInviteStatus.DECLINED || me.getStatus() == BeneficiaryInviteStatus.WAIVED) {
            throw new BusinessException(ErrorCode.CLAIM_ALREADY_RESPONDED, "Bạn đã từ chối nhận di sản này.");
        }
        // N2-access: bỏ tài sản đã phân bổ cho người khác; tài sản chưa phân bổ thì ai cũng thấy
        Set<UUID> hidden = new HashSet<>(assetAllocationRepository.findAssetIdsNotHeldBy(vault.getId(), me.getId()));
        return executorTaskRepository.findByVaultIds(List.of(vault.getId())).stream()
                .filter(t -> !hidden.contains(t.getAsset().getId()))
                .map(ExecutorTaskServiceImpl::toResponse)
                .toList();
    }

    // ---------- C.6: xác nhận chéo hai phía ----------

    @Override
    @Transactional
    public ExecutorTaskResponse confirmByBeneficiary(UUID vaultId, UUID taskId, UUID currentUserId) {
        BeneficiaryTaskAccess access = requireBeneficiaryTask(vaultId, taskId, currentUserId);
        ExecutorTask task = access.task();
        if (task.getStatus() != ExecutorTaskStatus.DONE) {
            throw new BusinessException(ErrorCode.CONFLICT, "Executor chưa báo hoàn tất việc này.");
        }
        if (task.getConfirmationStatus() == TaskConfirmationStatus.CONFIRMED) {
            return toResponse(task);
        }
        if (task.getConfirmationStatus() == TaskConfirmationStatus.DISPUTED) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Việc này đang bị phản đối, Executor cần xử lý lại trước.");
        }
        task.setConfirmationStatus(TaskConfirmationStatus.CONFIRMED);
        task.setConfirmedAt(LocalDateTime.now());
        task.setConfirmedBy(currentUserId);
        ExecutorTask saved = executorTaskRepository.saveAndFlush(task);

        auditLogService.log(AuditAction.EXECUTOR_TASK_CONFIRMED, AuditResult.SUCCESS, currentUserId,
                access.me().getEmail(), task.getVault().getId(), "ExecutorTask", task.getId().toString(),
                "CONFIRMED");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ExecutorTaskResponse disputeByBeneficiary(UUID vaultId, UUID taskId, DisputeExecutorTaskRequest request,
                                                     UUID currentUserId) {
        String reason = request == null || request.getReason() == null ? "" : request.getReason().trim();
        if (reason.length() < 5 || reason.length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Lý do phản đối từ 5 đến 500 ký tự.");
        }
        BeneficiaryTaskAccess access = requireBeneficiaryTask(vaultId, taskId, currentUserId);
        ExecutorTask task = access.task();
        if (task.getStatus() != ExecutorTaskStatus.DONE) {
            throw new BusinessException(ErrorCode.CONFLICT, "Executor chưa báo hoàn tất việc này.");
        }
        if (task.getConfirmationStatus() == TaskConfirmationStatus.DISPUTED) {
            return toResponse(task);
        }
        if (task.getConfirmationStatus() == TaskConfirmationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.CONFLICT, "Việc này đã được xác nhận, không thể phản đối.");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = task.getConfirmDeadlineAt() != null ? task.getConfirmDeadlineAt()
                : task.getCompletedAt() == null ? null : task.getCompletedAt().plusDays(confirmDays);
        if (deadline != null && now.isAfter(deadline)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Đã hết thời hạn phản đối (" + confirmDays + " ngày).");
        }

        task.setStatus(ExecutorTaskStatus.DOING);
        task.setCompletedAt(null);
        task.setConfirmationStatus(TaskConfirmationStatus.DISPUTED);
        task.setConfirmDeadlineAt(null);
        task.setConfirmedAt(null);
        task.setConfirmedBy(null);
        task.setDisputeReason(reason);
        task.setDisputedAt(now);
        task.setDisputedBy(currentUserId);
        ExecutorTask saved = executorTaskRepository.saveAndFlush(task);

        auditLogService.log(AuditAction.EXECUTOR_TASK_DISPUTED, AuditResult.SUCCESS, currentUserId,
                access.me().getEmail(), task.getVault().getId(), "ExecutorTask", task.getId().toString(),
                "DONE -> DOING");
        eventPublisher.publishEvent(new ExecutorTaskDisputedEvent(saved.getId(), task.getVault().getId(),
                task.getVault().getOwner().getId(), task.getVault().getName(), task.getPlatform(), reason));
        return toResponse(saved);
    }

    private record BeneficiaryTaskAccess(ExecutorTask task, VaultBeneficiary me) {
    }

    // Cùng quy tắc truy cập với listForBeneficiary; khóa dòng task để hai người không xác nhận / phản đối cùng lúc
    private BeneficiaryTaskAccess requireBeneficiaryTask(UUID vaultId, UUID taskId, UUID userId) {
        Vault vault = vaultRepository.findById(vaultId)
                .filter(v -> v.getStatus() != VaultStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, VAULT_NOT_FOUND));
        VaultBeneficiary me = vaultBeneficiaryRepository.findByVaultIdAndUserId(vault.getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, VAULT_NOT_FOUND));
        if (me.getStatus() == BeneficiaryInviteStatus.DECLINED || me.getStatus() == BeneficiaryInviteStatus.WAIVED) {
            throw new BusinessException(ErrorCode.CLAIM_ALREADY_RESPONDED, "Bạn đã từ chối nhận di sản này.");
        }
        if (vault.getStatus() == VaultStatus.ARCHIVED_LOCKED) {
            throw new BusinessException(ErrorCode.VAULT_STATE_CONFLICT, "Vault đã hết hạn bàn giao.");
        }
        ExecutorTask task = executorTaskRepository.findByIdForUpdate(taskId)
                .filter(t -> t.getVault().getId().equals(vault.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, TASK_NOT_FOUND));
        Set<UUID> hidden = new HashSet<>(assetAllocationRepository.findAssetIdsNotHeldBy(vault.getId(), me.getId()));
        if (hidden.contains(task.getAsset().getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, TASK_NOT_FOUND);
        }
        return new BeneficiaryTaskAccess(task, me);
    }

    // Bằng chứng "mới": mã tham chiếu khác mã đã lưu, hoặc tệp được tải SAU lúc bị phản đối
    private static boolean hasFreshEvidence(ExecutorTask task, String evidenceRef) {
        if (evidenceRef != null && !evidenceRef.equals(task.getEvidenceRef())) {
            return true;
        }
        return task.getEvidenceUploadedAt() != null && task.getDisputedAt() != null
                && task.getEvidenceUploadedAt().isAfter(task.getDisputedAt());
    }

    static ExecutorTaskWish parseWish(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ExecutorTaskWish.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String helpUrl(String platform, ExecutorTaskWish wish) {
        if (platform == null) {
            return null;
        }
        if ("Facebook".equals(platform) && wish == ExecutorTaskWish.MEMORIALIZE) {
            return FACEBOOK_MEMORIALIZE_URL;
        }
        return HELP_URLS.get(platform);
    }

    // package-private: ExecutorEvidenceServiceImpl dùng lại
    static ExecutorTaskResponse toResponse(ExecutorTask t) {
        LocalDateTime now = LocalDateTime.now();
        boolean autoConfirmed = t.getConfirmationStatus() == TaskConfirmationStatus.PENDING
                && t.getConfirmDeadlineAt() != null && t.getConfirmDeadlineAt().isBefore(now);
        return ExecutorTaskResponse.builder()
                .id(t.getId())
                .vaultId(t.getVault().getId())
                .vaultName(t.getVault().getName())
                .assetType(t.getAsset().getAssetType())
                .platform(t.getPlatform())
                .accountHandle(t.getAccountHandle())
                .wish(t.getWish())
                .status(t.getStatus())
                .note(t.getNote())
                .helpUrl(helpUrl(t.getPlatform(), t.getWish()))
                .updatedAt(t.getUpdatedAt())
                .completedAt(t.getCompletedAt())
                .evidenceRef(t.getEvidenceRef())
                .hasEvidenceDocument(t.getEvidenceDocumentId() != null)
                .evidenceFileName(t.getEvidenceFileName())
                .evidenceSha256(t.getEvidenceSha256())
                .confirmationStatus(t.getConfirmationStatus())
                .confirmDeadlineAt(t.getConfirmDeadlineAt())
                .confirmedAt(t.getConfirmedAt())
                .disputeReason(t.getDisputeReason())
                .disputedAt(t.getDisputedAt())
                .autoConfirmed(autoConfirmed)
                .build();
    }
}
