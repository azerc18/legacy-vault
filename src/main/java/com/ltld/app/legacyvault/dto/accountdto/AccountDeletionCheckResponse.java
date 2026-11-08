package com.ltld.app.legacyvault.dto.accountdto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// Những lý do đang chặn việc xoá tài khoản (rỗng = xoá được)
// VAULTS_OWNED: còn két; EXECUTOR: còn là người thi hành; BENEFICIARY: còn là người thụ hưởng;
// STAFF_ROLE: tài khoản Admin / Legal Verifier
@Getter
@Builder
public class AccountDeletionCheckResponse {
    private boolean deletable;
    private List<String> blockers;
    private long vaultCount;
    private boolean mfaRequired;
}
