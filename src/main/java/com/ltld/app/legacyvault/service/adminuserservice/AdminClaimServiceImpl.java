package com.ltld.app.legacyvault.service.adminuserservice;

import com.ltld.app.legacyvault.dto.adminuserdto.ClaimExtensionResponse;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminClaimServiceImpl implements AdminClaimService {

    static final int MAX_EXTENSIONS = 2;
    private static final String ENTITY_VAULT = "Vault";

    private final VaultRepository vaultRepository;
    private final BeneficiaryClaimRepository claimRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public ClaimExtensionResponse extendClaim(UUID adminId, UUID vaultId, int days, String reason) {
        // Khoá vault trước rồi mới đụng claim: cùng thứ tự với ClaimTimeoutScheduler / closeVault (tránh deadlock)
        Vault vault = vaultRepository.findByIdForUpdate(vaultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Vault not found"));

        if (vault.getStatus() != VaultStatus.UNLOCKED || vault.getClaimDeadlineAt() == null) {
            throw new BusinessException(ErrorCode.VAULT_STATE_CONFLICT,
                    "Only unlocked vaults that are waiting for beneficiaries can be extended");
        }
        if (vault.getClaimExtensions() >= MAX_EXTENSIONS) {
            throw new BusinessException(ErrorCode.CLAIM_EXTEND_LIMIT,
                    "The claim period can be extended at most " + MAX_EXTENSIONS + " times");
        }

        // Hạn có thể vừa qua nhưng scheduler (mỗi giờ) chưa kịp archive -> tính từ bây giờ
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = vault.getClaimDeadlineAt().isAfter(now) ? vault.getClaimDeadlineAt() : now;
        LocalDateTime newDeadline = base.plusDays(days);

        vault.setClaimDeadlineAt(newDeadline);
        vault.setClaimExtensions(vault.getClaimExtensions() + 1);
        vaultRepository.save(vault);

        // Chỉ gia hạn người chưa phản hồi; CLAIMED / DECLINED / EXPIRED giữ nguyên
        claimRepository.findAllByVaultId(vaultId).stream()
                .filter(c -> c.getStatus() == ClaimStatus.PENDING)
                .forEach(c -> c.setClaimDeadlineAt(newDeadline));

        auditLogService.log(AuditAction.CLAIM_EXTENDED, AuditResult.SUCCESS, adminId, null,
                vaultId, ENTITY_VAULT, vaultId.toString(),
                "extension " + vault.getClaimExtensions() + "/" + MAX_EXTENSIONS
                        + ", +" + days + " days, newDeadline=" + newDeadline + ", reason=" + reason);

        return new ClaimExtensionResponse(vaultId, newDeadline, vault.getClaimExtensions(),
                MAX_EXTENSIONS - vault.getClaimExtensions());
    }
}
