package com.ltld.app.legacyvault.dto.beneficiarydto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/** #10: Admin mở khóa xác minh danh tính của một người thụ hưởng cho một vault. */
@Data
public class UnlockBeneficiaryRequest {
    @NotNull
    private UUID vaultId;

    @NotNull
    private UUID beneficiaryId;

    @Size(max = 500)
    private String reason;
}
