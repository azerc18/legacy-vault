package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.DigitalSignature;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DigitalSignatureRepository extends JpaRepository<DigitalSignature, UUID> {

    Optional<DigitalSignature> findByRequestId(UUID requestId);

    boolean existsByRequestId(UUID requestId);

    // X4: chữ ký của yêu cầu xác minh đã SIGNED gần nhất có chứa vault này
    @Query("""
            SELECT s FROM DigitalSignature s
            JOIN FETCH s.request r JOIN FETCH s.verifier JOIN FETCH r.executor
            WHERE r.status = com.ltld.app.legacyvault.enums.LegalVerificationStatus.SIGNED
              AND EXISTS (SELECT 1 FROM VerificationRequestVault rv WHERE rv.request = r AND rv.vault.id = :vaultId)
            ORDER BY s.signedAt DESC
            """)
    List<DigitalSignature> findSignedForVault(@Param("vaultId") UUID vaultId, Pageable pageable);
}