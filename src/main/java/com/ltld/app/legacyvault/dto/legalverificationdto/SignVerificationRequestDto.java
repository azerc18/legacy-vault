package com.ltld.app.legacyvault.dto.legalverificationdto;

import com.ltld.app.legacyvault.enums.SignatureMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SignVerificationRequestDto {

    @NotNull(message = "Vui lòng chọn phương thức ký")
    private SignatureMethod method;

    @NotBlank(message = "Vui lòng nhập mã xác nhận")
    @Pattern(regexp = "\\d{6}", message = "Mã xác nhận phải gồm đúng 6 chữ số")
    private String code;
}