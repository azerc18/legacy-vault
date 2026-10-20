package com.ltld.app.legacyvault.dto.userdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Họ tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
    private String fullName;

    @Pattern(regexp = "^$|^[0-9+()\\-\\s]{8,20}$", message = "Số điện thoại không hợp lệ")
    private String phoneNumber;
}