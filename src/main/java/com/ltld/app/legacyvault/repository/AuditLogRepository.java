package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    // Lấy toàn bộ nhật ký của 1 người dùng, sắp xếp hành động mới nhất lên đầu (Desc)
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Lấy nhật ký của một Két sắt cụ thể (nếu cần thiết sau này)
    List<AuditLog> findByVaultIdOrderByCreatedAtDesc(UUID vaultId);
}
