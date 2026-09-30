package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.IdentityVerification;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, UUID> {

    Optional<IdentityVerification> findFirstByVaultIdAndBeneficiaryIdAndStatusAndCreatedAtAfter(
            UUID vaultId, UUID beneficiaryId, VerificationStatus status, LocalDateTime createdAfter);

}