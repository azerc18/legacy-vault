package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationResponse;
import com.ltld.app.legacyvault.dto.assetverificationdto.ConfirmVerificationRequest;
import com.ltld.app.legacyvault.dto.assetverificationdto.StartVerificationRequest;

import java.util.UUID;

public interface AssetVerificationService {

    AssetVerificationResponse getStatus(UUID ownerId, UUID vaultId, UUID assetId);

    AssetVerificationResponse start(UUID ownerId, UUID vaultId, UUID assetId, StartVerificationRequest request);

    AssetVerificationResponse confirm(UUID ownerId, UUID vaultId, UUID assetId, ConfirmVerificationRequest request);
}