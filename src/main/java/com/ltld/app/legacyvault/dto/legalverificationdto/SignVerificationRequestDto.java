package com.ltld.app.legacyvault.dto.legalverificationdto;

import com.ltld.app.legacyvault.enums.SignatureMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SignVerificationRequestDto {

    @NotNull(message = "Please select a signing method")
    private SignatureMethod method;

    @NotBlank(message = "Please enter the confirmation code")
    @Pattern(regexp = "\\d{6}", message = "Confirmation code must be exactly 6 digits")
    private String code;
}