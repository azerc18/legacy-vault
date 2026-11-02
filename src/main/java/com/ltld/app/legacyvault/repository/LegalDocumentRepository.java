package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.LegalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {
    //Hàm này giúp lấy toàn bộ tài liệu pháp lý nằm trong 1 két sắt
    List<LegalDocument> findByVaultId(UUID vaultid);

    // FR-18 (phần 2): lấy 1 tài liệu, bắt buộc thuộc đúng vault để không tải nhầm tài liệu của vault khác
    Optional<LegalDocument> findByIdAndVaultId(UUID id, UUID vaultId);

    // FR-18 (phần 2): danh sách có thứ tự ổn định (mới nhất trước)
    List<LegalDocument> findByVaultIdOrderByUploadedAtDescIdDesc(UUID vaultId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM LegalDocument d WHERE d.vault.id = :vaultId")
    int deleteByVaultId(@Param("vaultId") UUID vaultId);
    // FR-27/FR-20: Đếm số lượng tài liệu trong Vault
    long countByVaultId(UUID vaultId);

    // Chỉ lấy đường dẫn file, không nạp entity (để bulk delete sau đó không để lại entity "mồ côi" trong persistence context)
    @Query("SELECT d.fileUrlEncrypted FROM LegalDocument d WHERE d.vault.id = :vaultId")
    List<String> findFileUrlsByVaultId(@Param("vaultId") UUID vaultId);
}
