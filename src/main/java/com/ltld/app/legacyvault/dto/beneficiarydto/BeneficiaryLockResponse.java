package com.ltld.app.legacyvault.dto.beneficiarydto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** #10: một người thụ hưởng đang bị khóa xác minh danh tính (màn hình Admin). */
@Getter
@Builder
public class BeneficiaryLockResponse {
    private UUID vaultId;
    private String vaultName;
    private UUID beneficiaryId;
    private String beneficiaryEmail;
    private String beneficiaryName;
    private LocalDateTime lockedAt;
    private LocalDateTime lockedUntil;
    private Integer attempts;
}
