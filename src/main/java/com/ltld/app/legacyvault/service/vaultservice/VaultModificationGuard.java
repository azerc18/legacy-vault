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

import java.util.List;

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
     * V7: Kiểm tra lý do khóa không ném exception để trả về cho FE.
     * Dùng isHandoverStarted (chỉ đọc, không khóa) thay vì assertHandoverNotStarted
     * để tránh PESSIMISTIC_FORCE_INCREMENT bên trong @Transactional(readOnly=true)
     * gây UnexpectedRollbackException (HTTP 500) - C1.
     */
    public java.util.Optional<String> lockReason(Vault vault) {
        if (vault.getStatus() != VaultStatus.ACTIVE) {
            return java.util.Optional.of("Cannot modify vault that is not in active state");
        }
        if (dmsConfigService.isHandoverStarted(vault.getOwner().getId())) {
            return java.util.Optional.of("Handover process has started");
        }
        boolean hasOpenVerification = !verificationRequestVaultRepository
                .findVaultIdsWithOpenRequest(
                        List.of(vault.getId()),
                        List.of(LegalVerificationStatus.PENDING, LegalVerificationStatus.APPROVED))
                .isEmpty();
        if (hasOpenVerification) {
            return java.util.Optional.of("A verification request is pending or approved");
        }
        return java.util.Optional.empty();
    }

    public boolean isModifiable(Vault vault) {
        return lockReason(vault).isEmpty();
    }
}
