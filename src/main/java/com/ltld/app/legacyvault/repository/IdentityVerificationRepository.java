package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.IdentityVerification;
import com.ltld.app.legacyvault.enums.VerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, UUID> {

    Optional<IdentityVerification>
    findFirstByVaultIdAndBeneficiaryIdAndMethodAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID vaultId, UUID beneficiaryId, VerificationMethod method,
            VerificationStatus status, LocalDateTime createdAfter);

    // FR-17: phiên xác thực mới nhất theo vault + beneficiary + method + trạng thái
    Optional<IdentityVerification>
    findFirstByVaultIdAndBeneficiaryIdAndMethodAndStatusOrderByCreatedAtDesc(
            UUID vaultId, UUID beneficiaryId, VerificationMethod method, VerificationStatus status);

    // FR-17: có phiên nào ở trạng thái này không (dùng để kiểm tra "đã bị khóa" với FAILED)
    boolean existsByVaultIdAndBeneficiaryIdAndStatus(
            UUID vaultId, UUID beneficiaryId, VerificationStatus status);

    // FR-17: còn phiên xem hợp lệ không (SUCCESS và verified_at sau mốc thời gian)
    boolean existsByVaultIdAndBeneficiaryIdAndStatusAndVerifiedAtAfter(
            UUID vaultId, UUID beneficiaryId, VerificationStatus status, LocalDateTime verifiedAfter);

}