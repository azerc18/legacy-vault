package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.BeneficiaryClaimProgressResponse;
import com.ltld.app.legacyvault.entity.AssetAllocation;
import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.BeneficiaryClaimProgress;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.BeneficiaryReminderRequestedEvent;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutorClaimTrackingServiceImpl implements ExecutorClaimTrackingService {

    static final long REMINDER_INTERVAL_HOURS = 24;
    private static final String VAULT_NOT_FOUND = "Không tìm thấy Vault.";
    private static final String BENEFICIARY_NOT_FOUND = "Không tìm thấy người thụ hưởng.";

    private final ExecutorAccessGuard executorAccessGuard;
    private final VaultRepository vaultRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final BeneficiaryClaimRepository beneficiaryClaimRepository;
    private final AssetAllocationRepository assetAllocationRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<BeneficiaryClaimProgressResponse> listBeneficiaryProgress(UUID assignmentId, UUID vaultId,
                                                                          String executorLogin) {
        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);
        Vault vault = requireHandedOverVault(assignment, vaultId);
        return toResponses(vault, LocalDateTime.now());
    }

    @Override
    @Transactional
    public BeneficiaryClaimProgressResponse sendReminder(UUID assignmentId, UUID vaultId, UUID vaultBeneficiaryId,
                                                         String executorLogin) {
        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);
        Vault vault = requireHandedOverVault(assignment, vaultId);
        LocalDateTime now = LocalDateTime.now();
        if (!inClaimWindow(vault, now)) {
            throw new BusinessException(ErrorCode.VAULT_STATE_CONFLICT,
                    "Vault không còn trong thời gian nhận tài sản, không thể gửi nhắc nhở.");
        }

        BeneficiaryClaimProgressResponse target = find(toResponses(vault, now), vaultBeneficiaryId);
        if (target.getStatus() != BeneficiaryClaimProgress.PENDING) {
            throw new BusinessException(ErrorCode.CLAIM_ALREADY_RESPONDED,
                    "Người thụ hưởng đã phản hồi, không cần nhắc nữa.");
        }
        // Một câu UPDATE có điều kiện: hai lần bấm cùng lúc thì chỉ một lần ghi được
        if (vaultBeneficiaryRepository.markReminded(vaultBeneficiaryId, now,
                now.minusHours(REMINDER_INTERVAL_HOURS)) == 0) {
            throw new BusinessException(ErrorCode.REMINDER_TOO_SOON,
                    "Mỗi người thụ hưởng chỉ được nhắc 1 lần trong 24 giờ.");
        }

        eventPublisher.publishEvent(new BeneficiaryReminderRequestedEvent(vaultBeneficiaryId));
        auditLogService.log(AuditAction.BENEFICIARY_REMINDER_SENT, AuditResult.SUCCESS,
                assignment.getExecutorUser().getId(), assignment.getExecutorUser().getEmail(), vault.getId(),
                "VaultBeneficiary", vaultBeneficiaryId.toString(), null);
        return find(toResponses(vault, now), vaultBeneficiaryId);
    }

    // Chỉ Vault của Owner mà Executor phụ trách, và chỉ sau khi đã mở khóa bàn giao; khác đi thì 404
    private Vault requireHandedOverVault(ExecutorAssignment assignment, UUID vaultId) {
        return vaultRepository.findById(vaultId)
                .filter(v -> v.getOwner().getId().equals(assignment.getOwner().getId()))
                .filter(v -> v.getStatus() != VaultStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, VAULT_NOT_FOUND));
    }

    private static boolean inClaimWindow(Vault vault, LocalDateTime now) {
        return vault.getStatus() == VaultStatus.UNLOCKED
                && (vault.getClaimDeadlineAt() == null || now.isBefore(vault.getClaimDeadlineAt()));
    }

    private static BeneficiaryClaimProgressResponse find(List<BeneficiaryClaimProgressResponse> rows, UUID id) {
        return rows.stream()
                .filter(r -> r.getVaultBeneficiaryId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, BENEFICIARY_NOT_FOUND));
    }

    private List<BeneficiaryClaimProgressResponse> toResponses(Vault vault, LocalDateTime now) {
        Map<UUID, BeneficiaryClaim> claimByUser = new HashMap<>();
        for (BeneficiaryClaim claim : beneficiaryClaimRepository.findAllByVaultId(vault.getId())) {
            claimByUser.put(claim.getBeneficiary().getId(), claim);
        }
        // Người chính có ít nhất một phần tài sản đã chuyển sang người dự phòng
        Set<UUID> fellBack = new HashSet<>();
        for (AssetAllocation a : assetAllocationRepository.findByVaultId(vault.getId())) {
            if (a.getEffectiveBeneficiary() != null
                    && !a.getEffectiveBeneficiary().getId().equals(a.getBeneficiary().getId())) {
                fellBack.add(a.getBeneficiary().getId());
            }
        }

        boolean claimWindow = inClaimWindow(vault, now);
        return vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vault.getId()).stream().map(vb -> {
            BeneficiaryClaim claim = vb.getUser() == null ? null : claimByUser.get(vb.getUser().getId());
            BeneficiaryClaimProgress status = statusOf(vb, claim, fellBack.contains(vb.getId()));
            LocalDateTime nextAllowed = vb.getLastReminderAt() == null ? null
                    : vb.getLastReminderAt().plusHours(REMINDER_INTERVAL_HOURS);
            boolean tooSoon = nextAllowed != null && now.isBefore(nextAllowed);
            boolean pending = status == BeneficiaryClaimProgress.PENDING;
            return BeneficiaryClaimProgressResponse.builder()
                    .vaultBeneficiaryId(vb.getId())
                    .fullName(vb.getFullName())
                    .maskedEmail(maskEmail(vb.getEmail()))
                    .relationship(vb.getRelationship())
                    .status(status)
                    .respondedAt(vb.getRespondedAt())
                    .claimedAt(claim == null ? null : claim.getClaimedAt())
                    .lastReminderAt(vb.getLastReminderAt())
                    .nextReminderAllowedAt(pending && claimWindow && tooSoon ? nextAllowed : null)
                    .canRemind(pending && claimWindow && !tooSoon)
                    .build();
        }).toList();
    }

    static BeneficiaryClaimProgress statusOf(VaultBeneficiary vb, BeneficiaryClaim claim, boolean fellBack) {
        if (vb.getStatus() == BeneficiaryInviteStatus.DECLINED || vb.getStatus() == BeneficiaryInviteStatus.WAIVED
                || (claim != null && claim.getStatus() == ClaimStatus.DECLINED)) {
            return fellBack ? BeneficiaryClaimProgress.FALLBACK_APPLIED : BeneficiaryClaimProgress.DECLINED;
        }
        if (vb.getStatus() == BeneficiaryInviteStatus.ACCEPTED
                || (claim != null && claim.getStatus() == ClaimStatus.CLAIMED)) {
            return BeneficiaryClaimProgress.ACCEPTED;
        }
        return BeneficiaryClaimProgress.PENDING;
    }

    // "nguyenvana@gmail.com" -> "ng*******@gmail.com"; phần tên ngắn (<= 3 ký tự) chỉ giữ ký tự đầu
    static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        int keep = at <= 3 ? 1 : 2;
        return email.substring(0, keep) + "*".repeat(at - keep) + email.substring(at);
    }
}
