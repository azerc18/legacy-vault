package com.ltld.app.legacyvault.dto.adminuserdto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthorityResponse {
    private String code;
    private String description;
}
