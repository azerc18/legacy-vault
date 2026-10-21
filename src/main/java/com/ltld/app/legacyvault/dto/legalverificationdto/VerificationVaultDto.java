package com.ltld.app.legacyvault.dto.legalverificationdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Vault sẽ được mở khóa — kèm tên để verifier nhận ra (không chỉ UUID). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationVaultDto {
    private UUID id;
    private String name;
}
