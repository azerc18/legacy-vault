package com.ltld.app.legacyvault.dto.identitydto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PhoneOtpRequest {
    @NotBlank(message = "Phone number is required")
    @Size(max = 30, message = "Phone number is too long")
    private String phoneNumber;
}
