package com.ltld.app.legacyvault.dto.userdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
    private String fullName;

    @Pattern(regexp = "^$|^[0-9+()\\-\\s]{8,20}$", message = "Invalid phone number")
    private String phoneNumber;
}