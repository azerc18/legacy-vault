package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.TransferMechanism;

// X4: suy ra cơ chế từ loại tài sản cho dữ liệu cũ. Khi N3 merge, thay bằng AssetSchemaRegistry của Duy.
public final class TransferMechanisms {
    private TransferMechanisms() {}

    public static TransferMechanism resolve(TransferMechanism stored, AssetType type) {
        if (stored != null) return stored;
        if (type == null) return TransferMechanism.EXECUTOR_DISPOSITION;
        return switch (type) {
            case BANK_ACCOUNT, DOCUMENT -> TransferMechanism.LEGAL_CLAIM_DOSSIER;
            case CRYPTO_WALLET -> TransferMechanism.CRYPTOGRAPHIC_SPLIT_KEY;
            default -> TransferMechanism.EXECUTOR_DISPOSITION;   // SOCIAL_MEDIA, OTHER (+ EMAIL, E_WALLET sau N3)
        };
    }
}