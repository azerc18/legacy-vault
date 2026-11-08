package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.ExecutorEvidenceFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExecutorEvidenceFileRepository extends JpaRepository<ExecutorEvidenceFile, UUID> {

    Optional<ExecutorEvidenceFile> findByTaskId(UUID taskId);

    // Dùng khi purge Vault: lấy đường dẫn file TRƯỚC khi xóa dòng (FK cascade sẽ xóa dòng)
    @Query("SELECT e.fileUrlEncrypted FROM ExecutorEvidenceFile e WHERE e.vault.id = :vaultId")
    List<String> findFileUrlsByVaultId(@Param("vaultId") UUID vaultId);
}
