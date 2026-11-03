package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.enums.DocumentType;
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
    // Hàm này giúp lấy toàn bộ tài liệu pháp lý nằm trong 1 két sắt (kể cả các bản cũ, dùng khi xóa vault)
    List<LegalDocument> findByVaultId(UUID vaultid);

    // FR-18 (phần 2): lấy 1 tài liệu, bắt buộc thuộc đúng vault để không tải nhầm tài liệu của vault khác
    Optional<LegalDocument> findByIdAndVaultId(UUID id, UUID vaultId);

    // X5 (M2): người thụ hưởng chỉ tải bản tài liệu hiện hành thuộc đúng vault
    Optional<LegalDocument> findByIdAndVaultIdAndIsCurrentTrue(UUID id, UUID vaultId);

    // FR-18 (phần 2): danh sách có thứ tự ổn định (mới nhất trước)
    List<LegalDocument> findByVaultIdOrderByUploadedAtDescIdDesc(UUID vaultId);

    // X5: Danh sách tài liệu hiện hành của một Vault (chỉ các bản isCurrent = true)
    List<LegalDocument> findByVaultIdAndIsCurrentTrueOrderByUploadedAtDescIdDesc(UUID vaultId);

    // X5: Lấy toàn bộ lịch sử phiên bản của một nhóm tài liệu (mới nhất trước)
    List<LegalDocument> findByDocumentGroupIdOrderByVersionDesc(UUID documentGroupId);

    // X5: Tìm bản có version cao nhất trong một nhóm tài liệu
    Optional<LegalDocument> findFirstByDocumentGroupIdOrderByVersionDesc(UUID documentGroupId);

    // X5: Đếm tổng số phiên bản của một nhóm tài liệu
    long countByDocumentGroupId(UUID documentGroupId);

    // X5 (m3): Đếm số phiên bản của từng nhóm trong vault chỉ bằng 1 query
    @Query("""
            SELECT d.documentGroupId, COUNT(d) FROM LegalDocument d
            WHERE d.vault.id = :vaultId GROUP BY d.documentGroupId
            """)
    List<Object[]> countVersionsByGroup(@Param("vaultId") UUID vaultId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM LegalDocument d WHERE d.vault.id = :vaultId")
    int deleteByVaultId(@Param("vaultId") UUID vaultId);

    // FR-27/FR-20: Đếm tổng số bản ghi tài liệu trong Vault
    long countByVaultId(UUID vaultId);

    // X5 (M2): Đếm số lượng tài liệu hiện hành trong Vault
    long countByVaultIdAndIsCurrentTrue(UUID vaultId);

    // F1: id các vault của Owner có ít nhất 1 tài liệu thuộc loại này (một query cho cả Owner, tránh N+1)
    @Query("SELECT DISTINCT d.vault.id FROM LegalDocument d WHERE d.vault.owner.id = :ownerId AND d.documentType = :type")
    List<UUID> findVaultIdsWithDocumentTypeByOwnerId(@Param("ownerId") UUID ownerId,
                                                     @Param("type") DocumentType type);

    // Chỉ lấy đường dẫn file, không nạp entity (để bulk delete sau đó không để lại entity "mồ côi" trong persistence context)
    @Query("SELECT d.fileUrlEncrypted FROM LegalDocument d WHERE d.vault.id = :vaultId")
    List<String> findFileUrlsByVaultId(@Param("vaultId") UUID vaultId);
}
