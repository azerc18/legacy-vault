package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class VaultModificationGuard {

    private final DmsConfigRepository dmsConfigRepository;
    private final VerificationRequestVaultRepository verificationRequestVaultRepository;

    /**
     * Quy tắc nghiệp vụ dùng chung (m3):
     * Két sắt không được phép thay đổi (sửa thông tin, thêm/xóa tài liệu) khi:
     * 1. Két không ở trạng thái ACTIVE (BAD_REQUEST)
     * 2. Dead Man's Switch của Owner đã TRIGGERED (CONFLICT)
     * 3. Két đang có hồ sơ xác minh quyền thừa kế mở (PENDING hoặc APPROVED) (CONFLICT)
     */
    public void ensureModifiable(Vault vault) {
        if (vault.getStatus() != VaultStatus.ACTIVE) {
            throw new VaultException("Cannot modify vault that is not in active state", HttpStatus.BAD_REQUEST);
        }

        dmsConfigRepository.findByOwnerId(vault.getOwner().getId()).ifPresent(dms -> {
            if (dms.getStatus() == DmsStatus.TRIGGERED) {
                throw new VaultException(
                        "Cannot modify vault: dead-man switch has been triggered", HttpStatus.CONFLICT);
            }
        });

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
}
