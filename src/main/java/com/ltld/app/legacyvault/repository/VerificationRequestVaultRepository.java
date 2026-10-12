package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.VerificationRequestVault;
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

    // M1: lấy vault của nhiều hồ sơ trong 1 query (tránh N+1 ở danh sách)
    @Query("SELECT rv FROM VerificationRequestVault rv JOIN FETCH rv.vault WHERE rv.request.id IN :ids")
    List<VerificationRequestVault> findByRequestIdIn(@Param("ids") Collection<UUID> requestIds);

    // Lấy vault + owner trong 1 query khi ký (tránh N+1)
    @Query("SELECT rv FROM VerificationRequestVault rv JOIN FETCH rv.vault v JOIN FETCH v.owner WHERE rv.request.id = :id")
    List<VerificationRequestVault> findByRequestIdFetchVault(@Param("id") UUID requestId);
}