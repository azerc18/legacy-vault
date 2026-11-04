package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.AssetAllocation;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.event.AssetsReassignedEvent;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * N2-notify: sau cửa sổ phản hồi (mặc định 30 ngày kể từ lúc mở khóa), phần EXCLUSIVE của người thụ hưởng
 * chưa phản hồi được chuyển sang người dự phòng đang hoạt động (cùng cơ chế effective_beneficiary như Decline).
 * <p>
 * Luật:
 * - Người giữ chính "chưa phản hồi" = trạng thái PENDING_REGISTRATION / INVITED và chưa từng xác minh danh
 *   tính thành công cho vault này. Không dựa vào "có claim" vì BeneficiaryNotificationListener tạo sẵn claim
 *   PENDING cho mọi người đã có tài khoản ngay lúc mở khóa.
 * - Người nhận thay phải đã ACCEPTED, có tài khoản và claim còn PENDING (chưa đóng phần của mình).
 * - PROPORTIONAL và JOINT_CONSENSUS giữ nguyên (không có người dự phòng, Owner quyết định chia lại).
 * - Không đổi trạng thái của người quá hạn; chạy lại không chuyển lần hai vì chỉ chọn effective IS NULL.
 * Trùng khoảng 10 dòng với declineClaim (canReceive + vòng lặp chuyển); gộp lại ở PR refactor sau.
 */
@Service
public class AllocationTimeoutFallbackService {

    static final List<BeneficiaryInviteStatus> SILENT_STATUSES =
            List.of(BeneficiaryInviteStatus.PENDING_REGISTRATION, BeneficiaryInviteStatus.INVITED);
    // Số vault tối đa mỗi lần quét; đủ cho quy mô hiện tại
    static final int BATCH_SIZE = 200;

    private final VaultRepository vaultRepository;
    private final AssetAllocationRepository assetAllocationRepository;
    private final BeneficiaryClaimRepository claimRepository;
    private final IdentityVerificationRepository verificationRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final int responseWindowDays;

    public AllocationTimeoutFallbackService(VaultRepository vaultRepository,
                                            AssetAllocationRepository assetAllocationRepository,
                                            BeneficiaryClaimRepository claimRepository,
                                            IdentityVerificationRepository verificationRepository,
                                            AuditLogService auditLogService,
                                            ApplicationEventPublisher eventPublisher,
                                            @Value("${app.beneficiary.response-window-days:30}") int responseWindowDays) {
        this.vaultRepository = vaultRepository;
        this.assetAllocationRepository = assetAllocationRepository;
        this.claimRepository = claimRepository;
        this.verificationRepository = verificationRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.responseWindowDays = responseWindowDays;
    }

    public List<UUID> findDueVaultIds(LocalDateTime now) {
        return vaultRepository.findVaultIdsPastResponseWindow(
                now.minusDays(responseWindowDays), now, SILENT_STATUSES, PageRequest.of(0, BATCH_SIZE));
    }

    /** Trả về số phân bổ đã chuyển. Khóa vault trước (cùng thứ tự với declineClaim / closeVault). */
    @Transactional
    public int applyFallback(UUID vaultId, LocalDateTime now) {
        Vault vault = vaultRepository.findByIdForUpdate(vaultId).orElse(null);
        LocalDateTime cutoff = now.minusDays(responseWindowDays);
        // Kiểm tra lại sau khi khóa: trạng thái có thể đã đổi giữa lúc quét và lúc khóa
        if (vault == null || vault.getStatus() != VaultStatus.UNLOCKED
                || vault.getUnlockedAt() == null || !vault.getUnlockedAt().isBefore(cutoff)
                || (vault.getClaimDeadlineAt() != null && now.isAfter(vault.getClaimDeadlineAt()))) {
            return 0;
        }

        Map<UUID, Boolean> silentCache = new HashMap<>();
        Map<UUID, Boolean> activeCache = new HashMap<>();
        Map<UUID, Integer> counts = new LinkedHashMap<>();   // userId người nhận thay -> số tài sản
        int reassigned = 0;

        for (AssetAllocation allocation : assetAllocationRepository.findTimeoutFallbackCandidates(vaultId, SILENT_STATUSES)) {
            VaultBeneficiary from = allocation.getBeneficiary();
            VaultBeneficiary to = allocation.getContingentBeneficiary();
            if (to.getId().equals(from.getId())) {
                continue;
            }
            if (!silentCache.computeIfAbsent(from.getId(), id -> isSilent(vaultId, from))) {
                continue;
            }
            if (!activeCache.computeIfAbsent(to.getId(), id -> isActive(vaultId, to))) {
                continue;
            }
            allocation.setEffectiveBeneficiary(to);
            assetAllocationRepository.save(allocation);
            reassigned++;
            counts.merge(to.getUser().getId(), 1, Integer::sum);
            // Tác nhân là hệ thống (userId = null), như ClaimTimeoutScheduler
            auditLogService.log(AuditAction.ASSET_ALLOCATION_FALLBACK, AuditResult.SUCCESS, null, null,
                    vaultId, "Asset", allocation.getAsset().getId().toString(),
                    "from=" + from.getId() + ", to=" + to.getId() + ", reason=NO_RESPONSE");
        }

        if (reassigned > 0) {
            List<AssetsReassignedEvent.Receiver> receivers = new ArrayList<>();
            counts.forEach((userId, count) -> receivers.add(new AssetsReassignedEvent.Receiver(userId, count)));
            String ownerName = vault.getOwner() != null && vault.getOwner().getFullName() != null
                    ? vault.getOwner().getFullName() : "the owner";
            // Thông báo in-app và email được gửi sau khi giao dịch này commit (hai listener)
            eventPublisher.publishEvent(new AssetsReassignedEvent(vault.getId(), vault.getName(), ownerName,
                    receivers, AssetsReassignedEvent.Reason.NO_RESPONSE));
        }
        return reassigned;
    }

    // Chưa phản hồi: đúng trạng thái "im lặng" và chưa từng xác minh danh tính thành công cho vault này.
    // Không dựa vào việc có claim: claim PENDING được hệ thống tạo sẵn lúc mở khóa cho mọi người đã có tài khoản.
    private boolean isSilent(UUID vaultId, VaultBeneficiary vb) {
        if (!SILENT_STATUSES.contains(vb.getStatus())) {
            return false;
        }
        return vb.getUser() == null
                || !verificationRepository.existsByVaultIdAndBeneficiaryIdAndStatus(
                vaultId, vb.getUser().getId(), VerificationStatus.SUCCESS);
    }

    // Người nhận thay phải đã chủ động ACCEPTED, có tài khoản và claim còn PENDING (chưa đóng phần của mình)
    private boolean isActive(UUID vaultId, VaultBeneficiary vb) {
        if (vb.getUser() == null || vb.getStatus() != BeneficiaryInviteStatus.ACCEPTED) {
            return false;
        }
        return claimRepository.findByVaultIdAndBeneficiaryId(vaultId, vb.getUser().getId())
                .map(c -> c.getStatus() == ClaimStatus.PENDING)
                .orElse(false);
    }
}
