package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetFieldSpec;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetSchema;

import java.util.List;

/** GET /api/asset-types: schema loại tài sản + các cách xác thực loại đó hỗ trợ (FE vẽ màn hình xác thực từ đây). */
public record AssetTypeResponse(AssetType assetType, String label, TransferMechanism mechanism,
                                List<AssetFieldSpec> fields, List<AssetVerificationMethod> verificationMethods) {

    public static AssetTypeResponse of(AssetSchema schema, List<AssetVerificationMethod> methods) {
        return new AssetTypeResponse(schema.assetType(), schema.label(), schema.mechanism(),
                schema.fields(), methods);
    }
}
