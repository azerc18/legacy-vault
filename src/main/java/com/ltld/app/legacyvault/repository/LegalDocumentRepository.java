package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.LegalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
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

    // Đếm số tài liệu trong Vault
    int countByVaultId(UUID vaultId);
}
