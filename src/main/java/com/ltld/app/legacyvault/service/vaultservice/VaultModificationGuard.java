package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VaultModificationGuard {

    private final DmsConfigService dmsConfigService;
    private final VerificationRequestVaultRepository verificationRequestVaultRepository;

    /**
     * Quy tắc nghiệp vụ dùng chung (FR-04 / FR-27):
     * Két sắt không được phép thay đổi (sửa thông tin, thêm/xóa tài liệu) khi:
     * 1. Két không ở trạng thái ACTIVE (BAD_REQUEST)
     * 2. Dead Man's Switch của Owner đã TRIGGERED (gọi assertHandoverNotStarted) (CONFLICT)
     * 3. Két đang có hồ sơ xác minh quyền thừa kế mở (PENDING hoặc APPROVED) (CONFLICT)
     */
    public void ensureModifiable(Vault vault) {
        if (vault.getStatus() != VaultStatus.ACTIVE) {
            throw new VaultException("Cannot modify vault that is not in active state", HttpStatus.BAD_REQUEST);
        }

        // FR-04 / FR-27: gọi assertHandoverNotStarted để dùng chung một quy tắc và khóa DmsConfig
        dmsConfigService.assertHandoverNotStarted(vault.getOwner().getId());

        boolean hasOpenVerification = !verificationRequestVaultRepository
                .findVaultIdsWithOpenRequest(
                        List.of(vault.getId()),
                        List.of(LegalVerificationStatus.PENDING, LegalVerificationStatus.APPROVED))
                .isEmpty();
        if (hasOpenVerification) {
            throw new VaultException(
                    "Cannot modify vault while a verification request is pending or approved",
                    HttpStatus.CONFLICT);
        }
    }

    /**
     * FR-27: tính lockReason theo lô cho tất cả Vault của một Owner —
     * đọc DmsConfig 1 lần và batch query verification thay vì N+1.
     * Chỉ dùng cho hiển thị (chỉ đọc); KHÔNG dùng để quyết định ghi.
     * Vault không có trong map kết quả nghĩa là modifiable = true.
     */
    public Map<UUID, String> lockReasons(UUID ownerId, Collection<Vault> vaults) {
        Map<UUID, String> result = new HashMap<>();
        if (vaults.isEmpty()) return result;

        List<Vault> activeVaults = new ArrayList<>();
        for (Vault v : vaults) {
            if (v.getStatus() != VaultStatus.ACTIVE) {
                result.put(v.getId(), "This vault is no longer active.");
            } else {
                activeVaults.add(v);
            }
        }

        if (activeVaults.isEmpty()) {
            return result;
        }

        // 1. Đọc DMS 1 lần cho toàn bộ Owner (C1: isHandoverStarted không khóa, an toàn trong readOnly tx)
        boolean handoverStarted = dmsConfigService.isHandoverStarted(ownerId);

        // 2. Batch query verification cho tất cả active vault id
        List<UUID> activeVaultIds = activeVaults.stream().map(Vault::getId).toList();
        Set<UUID> openVerificationIds = handoverStarted
                ? Set.of()
                : new HashSet<>(verificationRequestVaultRepository.findVaultIdsWithOpenRequest(
                        activeVaultIds,
                        List.of(LegalVerificationStatus.PENDING, LegalVerificationStatus.APPROVED)));

        for (Vault v : activeVaults) {
            if (handoverStarted) {
                result.put(v.getId(), "The handover process has started.");
            } else if (openVerificationIds.contains(v.getId())) {
                result.put(v.getId(), "A verification request is pending or approved.");
            }
            // Không có entry = modifiable
        }
        return result;
    }

    /**
     * FR-27: Kiểm tra lý do khóa không ném exception để trả về cho FE.
     * Dùng isHandoverStarted (chỉ đọc, không khóa) thay vì assertHandoverNotStarted
     * để tránh PESSIMISTIC_FORCE_INCREMENT bên trong @Transactional(readOnly=true)
     * gây UnexpectedRollbackException (HTTP 500) - C1.
     * Cho một Vault đơn lẻ: delegate sang lockReasons để không trùng logic.
     */
    public Optional<String> lockReason(Vault vault) {
        Map<UUID, String> reasons = lockReasons(vault.getOwner().getId(), List.of(vault));
        return Optional.ofNullable(reasons.get(vault.getId()));
    }
}
