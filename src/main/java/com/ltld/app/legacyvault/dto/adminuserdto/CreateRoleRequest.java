package com.ltld.app.legacyvault.dto.adminuserdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateRoleRequest {
    @NotBlank(message = "Role code is required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,49}$",
            message = "Role code must be 3-50 characters: UPPERCASE letters, digits or _, starting with a letter")
    private String code;

    @NotBlank(message = "Role name is required")
    @Size(max = 100, message = "Role name must be at most 100 characters")
    private String name;

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    @NotEmpty(message = "A role must have at least one authority")
    private List<String> authorities;
}
