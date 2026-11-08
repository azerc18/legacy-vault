package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.ExecutorTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface    ExecutorTaskRepository extends JpaRepository<ExecutorTask, UUID> {

    // Kèm vault + asset (tên vault, loại tài sản) để không N+1 và đọc được sau khi transaction đóng
    @Query("""
            SELECT t FROM ExecutorTask t JOIN FETCH t.vault JOIN FETCH t.asset
            WHERE t.vault.id IN :vaultIds
            ORDER BY t.vault.name ASC, t.createdAt ASC, t.id ASC
            """)
    List<ExecutorTask> findByVaultIds(@Param("vaultIds") Collection<UUID> vaultIds);

    @Query("SELECT t.asset.id FROM ExecutorTask t WHERE t.asset.id IN :assetIds")
    Set<UUID> findAssetIdsWithTask(@Param("assetIds") Collection<UUID> assetIds);

    // Khóa dòng khi Beneficiary xác nhận / phản đối, tránh hai người bấm cùng lúc
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ExecutorTask t WHERE t.id = :id")
    Optional<ExecutorTask> findByIdForUpdate(@Param("id") UUID id);
}
