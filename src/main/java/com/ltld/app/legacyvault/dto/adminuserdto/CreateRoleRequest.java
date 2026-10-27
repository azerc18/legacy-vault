package com.ltld.app.legacyvault.dto.adminuserdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateRoleRequest {
    @NotBlank(message = "Mã role là bắt buộc")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,49}$",
            message = "Mã role gồm 3-50 ký tự IN HOA, số hoặc _, bắt đầu bằng chữ")
    private String code;

    @NotBlank(message = "Tên role là bắt buộc")
    @Size(max = 100, message = "Tên role tối đa 100 ký tự")
    private String name;

    @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
    private String description;

    @NotEmpty(message = "Role phải có ít nhất 1 quyền")
    private List<String> authorities;
}
