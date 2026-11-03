package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.BeneficiaryClaimProgress;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

// N2-exec: một dòng tiến độ nhận tài sản; email đã che một phần, Executor không cần địa chỉ đầy đủ
@Getter
@Builder
public class BeneficiaryClaimProgressResponse {
    private final UUID vaultBeneficiaryId;
    private final String fullName;
    private final String maskedEmail;
    private final String relationship;
    private final BeneficiaryClaimProgress status;
    private final LocalDateTime respondedAt;
    private final LocalDateTime claimedAt;
    private final LocalDateTime lastReminderAt;
    // Thời điểm sớm nhất được nhắc lại; null khi nhắc được ngay hoặc không còn cần nhắc
    private final LocalDateTime nextReminderAllowedAt;
    private final boolean canRemind;
}
