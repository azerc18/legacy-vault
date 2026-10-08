package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.VaultActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VaultActivityLogRepository extends JpaRepository<VaultActivityLog, UUID> {
    List<VaultActivityLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<VaultActivityLog> findByVaultIdOrderByCreatedAtDesc(UUID vaultId);
}
