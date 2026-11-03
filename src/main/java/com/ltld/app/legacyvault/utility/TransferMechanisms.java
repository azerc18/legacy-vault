package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetSchema;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetSchemaRegistry;

// X4: cơ chế lưu trên tài sản; thiếu (vd. OTHER) thì lấy theo schema N3, cuối cùng mặc định Người thi hành xử lý
public final class TransferMechanisms {
    private TransferMechanisms() {}

    public static TransferMechanism resolve(TransferMechanism stored, AssetType type) {
        if (stored != null) return stored;
        AssetSchema schema = type == null ? null : AssetSchemaRegistry.schemaOf(type);
        return schema != null && schema.mechanism() != null ? schema.mechanism() : TransferMechanism.EXECUTOR_DISPOSITION;
    }
}