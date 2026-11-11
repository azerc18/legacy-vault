package com.ltld.app.legacyvault.dto.adminoverviewdto;

import java.time.LocalDateTime;

/** Việc 3: hạn VIP mới sau khi admin tặng ngày. */
public record VipGrantResponse(String planCode, LocalDateTime expiresAt) {
}
