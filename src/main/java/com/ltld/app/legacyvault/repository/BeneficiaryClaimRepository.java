package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BeneficiaryClaimRepository extends JpaRepository<BeneficiaryClaim, UUID> {
    // Tìm kiếm hồ sơ claim dựa trên id của Vault
    Optional<BeneficiaryClaim> findByVaultId(UUID vaultId);
}