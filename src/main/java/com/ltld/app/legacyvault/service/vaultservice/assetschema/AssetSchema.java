package com.ltld.app.legacyvault.service.vaultservice.assetschema;

import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.TransferMechanism;

import java.util.List;

/** N3: schema của một loại tài sản. mechanism có thể null (loại OTHER). */
public record AssetSchema(AssetType assetType, String label, TransferMechanism mechanism,
                          List<AssetFieldSpec> fields) {
}
