package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.Vault;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VaultRepository extends JpaRepository<Vault, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vault v where v.id = :id")
    Optional<Vault> findByIdForUpdate(@Param("id") UUID id);

    // FR-02: các Vault đang chờ người thụ hưởng (theo email) đăng ký/xác minh tài khoản
    List<Vault> findByBeneficiaryEmailAndBeneficiaryIsNull(String beneficiaryEmail);
}
