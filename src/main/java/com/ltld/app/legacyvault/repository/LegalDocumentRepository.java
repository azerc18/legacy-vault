package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.LegalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {
    //Hàm này giúp lấy toàn bộ tài liệu pháp lý nằm trong 1 két sắt
    List<LegalDocument> findByVaultId(UUID vaultid);
}
