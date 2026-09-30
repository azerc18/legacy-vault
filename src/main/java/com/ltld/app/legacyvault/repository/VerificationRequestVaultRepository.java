package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VerificationRequestVaultRepository extends JpaRepository<VerificationRequestVault, UUID> {

    List<VerificationRequestVault> findByRequestId(UUID requestId);
}