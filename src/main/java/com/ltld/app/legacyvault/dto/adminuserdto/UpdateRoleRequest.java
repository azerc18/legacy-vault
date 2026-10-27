package com.ltld.app.legacyvault.dto.adminuserdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateRoleRequest {
    @NotBlank(message = "Role name is required")
    @Size(max = 100, message = "Role name must be at most 100 characters")
    private String name;

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    @NotEmpty(message = "A role must have at least one authority")
    private List<String> authorities;
}