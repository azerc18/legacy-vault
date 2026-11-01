package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetSchema;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetSchemaRegistry;
import com.ltld.app.legacyvault.utility.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** N3: FE lấy schema các loại tài sản để vẽ form động. */
@RestController
@RequestMapping("/api/asset-types")
public class AssetTypeController {

    @GetMapping
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<List<AssetSchema>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Asset types retrieved successfully", AssetSchemaRegistry.all()));
    }
}
