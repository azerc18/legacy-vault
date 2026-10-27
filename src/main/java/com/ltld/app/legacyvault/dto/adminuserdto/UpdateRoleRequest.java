package com.ltld.app.legacyvault.dto.adminuserdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateRoleRequest {
    @NotBlank(message = "Tên role là bắt buộc")
    @Size(max = 100, message = "Tên role tối đa 100 ký tự")
    private String name;

    @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
    private String description;

    @NotEmpty(message = "Role phải có ít nhất 1 quyền")
    private List<String> authorities;
}