package com.ltld.app.legacyvault.beneficiary.repository;

import com.ltld.app.legacyvault.beneficiary.entity.BeneficiaryClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BeneficiaryClaimRepository extends JpaRepository<BeneficiaryClaim, UUID> {

    Optional<BeneficiaryClaim> findByVaultId(UUID vaultId);
}
