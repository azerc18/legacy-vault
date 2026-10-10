package com.ltld.app.legacyvault.dto;
import com.ltld.app.legacyvault.enums.SignatureMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SignVerificationRequestDto {

    @NotNull
    private SignatureMethod method;

    @Pattern(regexp = "\\d{6}", message = "Mã xác nhận phải gồm đúng 6 chữ số")
    private String code;
}