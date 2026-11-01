package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.MfaBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MfaBackupCodeRepository extends JpaRepository<MfaBackupCode, UUID> {

    // Các mã còn dùng được của user
    List<MfaBackupCode> findByUserIdAndUsedAtIsNull(UUID userId);

    // Xoá hết khi tắt 2FA hoặc sinh lại bộ mã mới
    @Modifying
    @Query("DELETE FROM MfaBackupCode c WHERE c.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}