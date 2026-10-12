package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VerificationRequestVaultRepository extends JpaRepository<VerificationRequestVault, UUID> {

    // Lấy vault kèm owner để dùng trong toDto()
    @Query("SELECT rv FROM VerificationRequestVault rv JOIN FETCH rv.vault WHERE rv.request.id = :id")
    List<VerificationRequestVault> findByRequestId(@Param("id") UUID requestId);

    // Lấy vault + owner trong 1 query khi ký (tránh N+1)
    @Query("SELECT rv FROM VerificationRequestVault rv JOIN FETCH rv.vault v JOIN FETCH v.owner WHERE rv.request.id = :id")
    List<VerificationRequestVault> findByRequestIdFetchVault(@Param("id") UUID requestId);

    // FR-09 (4a): Vault đã nằm trong một yêu cầu đang mở (PENDING / APPROVED chưa ký) hay chưa
    boolean existsByVaultIdInAndRequestStatusIn(Collection<UUID> vaultIds,
                                                Collection<LegalVerificationStatus> statuses);
}