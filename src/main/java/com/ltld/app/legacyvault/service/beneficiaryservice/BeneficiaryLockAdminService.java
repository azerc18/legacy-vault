package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryLockResponse;
import com.ltld.app.legacyvault.entity.IdentityVerification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * #10: Admin xem và mở khóa sớm người thụ hưởng bị khóa xác minh danh tính (sai 5 lần / 24h, FR-17 3b).
 * Mở khóa = đánh dấu adminUnlockedAt trên các phiên FAILED còn hiệu lực và xóa lượt thử của các phiên PENDING
 * trong cửa sổ 24h, để người đó bắt đầu lại với đủ 5 lượt. Không đụng tới claim hay dữ liệu tài sản.
 */
@Service
@RequiredArgsConstructor
public class BeneficiaryLockAdminService {

    private final IdentityVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<BeneficiaryLockResponse> listActiveLocks() {
        LocalDateTime after = LocalDateTime.now().minusHours(BeneficiaryServiceImpl.LOCK_HOURS);
        // Một người có thể có nhiều phiên FAILED: chỉ giữ phiên mới nhất (query đã sắp xếp mới nhất trước)
        Map<String, BeneficiaryLockResponse> byPair = new LinkedHashMap<>();
        for (IdentityVerification v : verificationRepository.findActiveLocks(after)) {
            String key = v.getVault().getId() + ":" + v.getBeneficiary().getId();
            byPair.computeIfAbsent(key, k -> BeneficiaryLockResponse.builder()
                    .vaultId(v.getVault().getId())
                    .vaultName(v.getVault().getName())
                    .beneficiaryId(v.getBeneficiary().getId())
                    .beneficiaryEmail(v.getBeneficiary().getEmail())
                    .beneficiaryName(v.getBeneficiary().getFullName())
                    .lockedAt(v.getCreatedAt())
                    .lockedUntil(v.getCreatedAt().plusHours(BeneficiaryServiceImpl.LOCK_HOURS))
                    .attempts(v.getAttemptCount())
                    .build());
        }
        return List.copyOf(byPair.values());
    }

    @Transactional
    public void unlock(UUID adminId, UUID vaultId, UUID beneficiaryId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime after = now.minusHours(BeneficiaryServiceImpl.LOCK_HOURS);
        int unlocked = verificationRepository.markLocksUnlocked(vaultId, beneficiaryId, after, now);
        if (unlocked == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "This beneficiary is not locked for this vault.");
        }
        verificationRepository.resetPendingAttempts(vaultId, beneficiaryId, after);

        String adminEmail = userRepository.findById(adminId).map(User::getEmail).orElse(null);
        String detail = "beneficiaryId=" + beneficiaryId
                + (reason == null || reason.isBlank() ? "" : ", reason=" + reason.strip());
        auditLogService.log(AuditAction.IDENTITY_UNLOCKED_BY_ADMIN, AuditResult.SUCCESS, adminId, adminEmail,
                vaultId, "Vault", vaultId.toString(), detail);
    }
}
