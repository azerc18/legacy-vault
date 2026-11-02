package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.BeneficiaryClaimProgressResponse;

import java.util.List;
import java.util.UUID;

// N2-exec: Executor theo dõi tiến độ nhận tài sản của từng người thụ hưởng và gửi nhắc nhở
public interface ExecutorClaimTrackingService {

    List<BeneficiaryClaimProgressResponse> listBeneficiaryProgress(UUID assignmentId, UUID vaultId,
                                                                   String executorLogin);

    BeneficiaryClaimProgressResponse sendReminder(UUID assignmentId, UUID vaultId, UUID vaultBeneficiaryId,
                                                  String executorLogin);
}
