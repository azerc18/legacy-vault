package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.vaultdto.AssetTypeResponse;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.service.assetverificationservice.AssetVerificationService;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetSchemaRegistry;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** N3: FE lấy schema các loại tài sản để vẽ form động. C.5: kèm các cách xác thực mỗi loại hỗ trợ. */
@RestController
@RequestMapping("/api/asset-types")
@RequiredArgsConstructor
public class AssetTypeController {

    private final AssetVerificationService assetVerificationService;

    @GetMapping
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<List<AssetTypeResponse>>> list() {
        Map<AssetType, List<AssetVerificationMethod>> methods = assetVerificationService.methodsByType();
        List<AssetTypeResponse> body = AssetSchemaRegistry.all().stream()
                .map(s -> AssetTypeResponse.of(s, methods.getOrDefault(s.assetType(), List.of())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Asset types retrieved successfully", body));
    }
}
