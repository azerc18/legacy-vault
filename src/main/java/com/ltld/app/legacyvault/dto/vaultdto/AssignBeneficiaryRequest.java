package com.ltld.app.legacyvault.dto.vaultdto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssignBeneficiaryRequest {
    @Email(message = "Email không đúng định dạng")
    @NotBlank(message = "Email người thụ hưởng là bắt buộc")
    private String email;

    // FR-02 (2a): Vault đã có người thụ hưởng thì Owner phải xác nhận ghi đè
    private Boolean confirmReplace;
}
