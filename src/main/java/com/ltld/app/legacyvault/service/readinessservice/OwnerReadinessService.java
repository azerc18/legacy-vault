package com.ltld.app.legacyvault.service.readinessservice;

import com.ltld.app.legacyvault.dto.readinessdto.ReadinessResponse;

import java.util.UUID;

/**
 * F1: Điểm sẵn sàng thừa kế (Estate Readiness Score - tham chiếu EstateOS Plan Score) trên thang 100 điểm.
 */
public interface OwnerReadinessService {

    ReadinessResponse evaluateReadiness(UUID ownerId);
}

