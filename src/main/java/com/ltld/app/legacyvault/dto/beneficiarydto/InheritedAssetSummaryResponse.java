package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class InheritedAssetSummaryResponse {
    private UUID id;
    private AssetType assetType;
    private String assetName;
    // X1: chỉ có với tài sản JOINT_CONSENSUS (null với tài sản thường)
    private ConsensusStatusResponse consensus;
    // C.6: trạng thái xác thực lúc bàn giao (từ bản chụp); null nếu chưa có bản chụp
    private AssetVerificationStatus verificationStatus;
    private AssetVerificationMethod verificationMethod;
    private LocalDateTime verifiedAt;
}
