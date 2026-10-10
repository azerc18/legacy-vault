package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VerificationRequestVaultRepository extends JpaRepository<VerificationRequestVault, UUID> {

    List<VerificationRequestVault> findByRequestId(UUID requestId);

    // Lấy luôn Vault trong một query, tránh N+1
    @Query("SELECT rv FROM VerificationRequestVault rv JOIN FETCH rv.vault WHERE rv.request.id = :id")
    List<VerificationRequestVault> findByRequestIdFetchVault(@Param("id") UUID requestId);
}