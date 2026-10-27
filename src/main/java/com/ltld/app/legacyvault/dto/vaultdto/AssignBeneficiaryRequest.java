package com.ltld.app.legacyvault.dto.vaultdto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssignBeneficiaryRequest {
    @Email(message = "Invalid email format")
    @NotBlank(message = "Beneficiary email is required")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    // FR-02 (2a): Vault đã có người thụ hưởng thì Owner phải xác nhận ghi đè
    private Boolean confirmReplace;
}
