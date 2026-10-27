package com.ltld.app.legacyvault.dto.adminuserdto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class RoleResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private boolean system;
    private List<String> authorities;
}
