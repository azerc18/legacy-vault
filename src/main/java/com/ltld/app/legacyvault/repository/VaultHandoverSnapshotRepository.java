package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.VaultHandoverSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VaultHandoverSnapshotRepository extends JpaRepository<VaultHandoverSnapshot, UUID> {

    Optional<VaultHandoverSnapshot> findByVaultId(UUID vaultId);
}
