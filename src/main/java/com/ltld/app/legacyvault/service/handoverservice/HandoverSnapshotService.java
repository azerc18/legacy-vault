package com.ltld.app.legacyvault.service.handoverservice;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

// C.6: bản chụp bất biến các trường công khai + trạng thái xác thực của tài sản lúc Vault mở khóa
public interface HandoverSnapshotService {

    /** Tạo bản chụp cho các Vault vừa mở khóa. Idempotent (mỗi Vault một bản); lỗi từng Vault chỉ log. */
    void createForVaults(Collection<UUID> vaultIds);

    /**
     * Trả về bản chụp của Vault, tạo mới nếu chưa có (Vault mở khóa trước khi có tính năng này).
     * Empty nếu Vault chưa mở khóa hoặc đã lưu trữ. Chạy trong transaction riêng nên đọc được ngay cả khi
     * người gọi đã mở transaction trước đó (MySQL REPEATABLE READ).
     */
    Optional<HandoverSnapshotView> ensureSnapshot(UUID vaultId);

    /** Tính lại hash từ dữ liệu đã lưu và so với hash lúc chụp. false nếu chưa có bản chụp hoặc dữ liệu bị đổi. */
    boolean verifyIntegrity(UUID vaultId);
}
