package com.ltld.app.legacyvault.event;

import java.util.List;
import java.util.UUID;

/**
 * N2-notify: phát sau khi Decline chuyển phân bổ sang người dự phòng. Chỉ mang dữ liệu thuần (không entity)
 * để listener chạy sau commit không phải chạm vào entity lazy.
 */
public record AssetsReassignedEvent(UUID vaultId, String vaultName, String ownerName, List<Receiver> receivers) {

    /** Người nhận thay (đã có tài khoản) và số tài sản họ nhận thêm. */
    public record Receiver(UUID userId, int assetCount) {
    }
}