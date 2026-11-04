package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorTaskResponse;
import com.ltld.app.legacyvault.dto.executordto.UpdateExecutorTaskRequest;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.ExecutorTask;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ExecutorTaskStatus;
import com.ltld.app.legacyvault.enums.ExecutorTaskWish;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.ExecutorTaskRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
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
    private final AuditLogService auditLogService;

    @Override
    @Transactional
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
            executorTaskRepository.save(ExecutorTask.builder()
                    .vault(asset.getVault())
                    .asset(asset)
                    .platform(fields.get("platform"))
                    .accountHandle(fields.get("usernameOrHandle"))
                    .wish(wish)
                    .build());
            created++;
        }
        return created;
    }

    @Override
    @Transactional
    public List<ExecutorTaskResponse> listForExecutor(UUID assignmentId, String executorLogin) {
        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);
        List<UUID> vaultIds = vaultRepository.findByOwnerIdOrderByCreatedAtDesc(assignment.getOwner().getId())
                .stream()
                .filter(v -> v.getStatus() != VaultStatus.ACTIVE)
                .map(Vault::getId)
                .toList();
        if (vaultIds.isEmpty()) {
            return List.of();
        }
        // Vault mở khóa trước khi có F5 (hoặc listener lỗi) vẫn có checklist khi Executor mở ra
        generateForVaults(vaultIds);
        return executorTaskRepository.findByVaultIds(vaultIds).stream().map(ExecutorTaskServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional
    public ExecutorTaskResponse update(UUID assignmentId, UUID taskId, UpdateExecutorTaskRequest request,
                                       String executorLogin) {
        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);
        if (request == null || request.getStatus() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Vui lòng chọn trạng thái.");
        }
        ExecutorTask task = executorTaskRepository.findById(taskId)
                .filter(t -> t.getVault().getOwner().getId().equals(assignment.getOwner().getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, TASK_NOT_FOUND));
        if (task.getVault().getStatus() == VaultStatus.ARCHIVED_LOCKED) {
            throw new BusinessException(ErrorCode.VAULT_STATE_CONFLICT,
                    "Vault đã hết hạn bàn giao, không thể cập nhật checklist.");
        }

        String note = request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim();
        if (note != null && note.length() > 1000) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Ghi chú tối đa 1000 ký tự.");
        }
        ExecutorTaskStatus previous = task.getStatus();
        task.setStatus(request.getStatus());
        task.setNote(note);
        if (request.getStatus() == ExecutorTaskStatus.DONE) {
            if (task.getCompletedAt() == null) {
                task.setCompletedAt(LocalDateTime.now());
            }
        } else {
            task.setCompletedAt(null);
        }
        ExecutorTask saved = executorTaskRepository.saveAndFlush(task);

        auditLogService.log(AuditAction.EXECUTOR_TASK_UPDATED, AuditResult.SUCCESS,
                assignment.getExecutorUser().getId(), assignment.getExecutorUser().getEmail(), task.getVault().getId(),
                "ExecutorTask", task.getId().toString(), previous + " -> " + request.getStatus());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutorTaskResponse> listForBeneficiary(UUID vaultId, UUID currentUserId) {
        // Không phải người thụ hưởng của Vault, hoặc Vault chưa bàn giao: 404 để không lộ Vault có tồn tại
        Vault vault = vaultRepository.findById(vaultId)
                .filter(v -> v.getStatus() != VaultStatus.ACTIVE)
                .filter(v -> vaultBeneficiaryRepository.findByVaultIdAndUserId(v.getId(), currentUserId).isPresent())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, VAULT_NOT_FOUND));
        return executorTaskRepository.findByVaultIds(List.of(vault.getId())).stream()
                .map(ExecutorTaskServiceImpl::toResponse)
                .toList();
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

    private static ExecutorTaskResponse toResponse(ExecutorTask t) {
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
                .build();
    }
}
