package com.ltld.app.legacyvault.dto.otpdto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendOtpRequest {
    @Email(message = "This is not a valid email format.")
    @NotBlank(message = "This field is required")
    private String email;
}
