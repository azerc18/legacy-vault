package com.ltld.app.legacyvault.dto.vaultdto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddBeneficiaryRequest {
    @Email(message = "Invalid email format")
    @NotBlank(message = "Beneficiary email is required")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    @Size(max = 255, message = "Full name must be at most 255 characters")
    private String fullName;

    @Size(max = 50, message = "Relationship must be at most 50 characters")
    private String relationship;
}