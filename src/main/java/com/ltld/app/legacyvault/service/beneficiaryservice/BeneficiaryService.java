package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.AssetDownloadResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryVaultSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultPreviewResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetDetailResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.LegalDocumentSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityResponse;

import java.util.List;
import java.util.UUID;

public interface BeneficiaryService {
    List<BeneficiaryVaultSummaryResponse> listMyVaults(UUID currentUserId);

    BeneficiaryClaimResponse initializeClaim(BeneficiaryClaimRequest request, UUID currentUserId);

    void sendIdentityOtp(BeneficiaryClaimRequest request, UUID currentUserId);

    VerifyIdentityResponse verifyIdentity(VerifyIdentityRequest request, UUID currentUserId);

    List<InheritedAssetSummaryResponse> getInheritedAssets(UUID vaultId, UUID currentUserId);

    InheritedAssetDetailResponse getInheritedAssetDetail(UUID vaultId, UUID assetId, UUID currentUserId);

    AssetDownloadResponse downloadInheritedAsset(UUID vaultId, UUID assetId, UUID currentUserId);

    List<LegalDocumentSummaryResponse> getVaultDocuments(UUID vaultId, UUID currentUserId);

    DocumentDownloadResponse downloadVaultDocument(UUID vaultId, UUID documentId, UUID currentUserId);

    CloseVaultResponse closeVault(UUID vaultId, CloseVaultRequest request, UUID currentUserId);

    CloseVaultPreviewResponse previewClose(UUID vaultId, UUID currentUserId);
}