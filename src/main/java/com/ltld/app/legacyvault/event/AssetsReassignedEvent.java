package com.ltld.app.legacyvault.event;

import java.util.List;
import java.util.UUID;

/**
 * N2-notify: phát sau khi phân bổ được chuyển sang người dự phòng (do Decline hoặc do không phản hồi).
 * Chỉ mang dữ liệu thuần (không entity) để listener chạy sau commit không phải chạm vào entity lazy.
 */
public record AssetsReassignedEvent(UUID vaultId, String vaultName, String ownerName,
                                    List<Receiver> receivers, Reason reason) {

    public enum Reason { DECLINED, NO_RESPONSE }

    // Giữ constructor 4 tham số cũ cho luồng Decline (BeneficiaryServiceImpl) và các test hiện có
    public AssetsReassignedEvent(UUID vaultId, String vaultName, String ownerName, List<Receiver> receivers) {
        this(vaultId, vaultName, ownerName, receivers, Reason.DECLINED);
    }

    /** Người nhận thay (đã có tài khoản) và số tài sản họ nhận thêm. */
    public record Receiver(UUID userId, int assetCount) {
    }
}