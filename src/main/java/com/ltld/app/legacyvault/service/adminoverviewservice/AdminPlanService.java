package com.ltld.app.legacyvault.service.adminoverviewservice;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPlanResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.UpdatePlanPriceRequest;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * C1: Quản lý gói. CHỈ đổi tên hiển thị + giá (áp dụng cho lần mua sau).
 * Không cho sửa giới hạn (số kho, người thụ hưởng, dung lượng...) vì gói đang chạy đọc giới hạn trực tiếp từ
 * bản ghi subscription_plans -> sửa sẽ đổi luôn quyền lợi của người đã mua. Muốn đổi giới hạn cần thêm "phiên bản gói".
 */
@Service
@RequiredArgsConstructor
public class AdminPlanService {

    @PersistenceContext
    private EntityManager em;

    private final SubscriptionPlanRepository planRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<AdminPlanResponse> list() {
        Map<UUID, Long> active = new HashMap<>();
        em.createQuery("""
                        SELECT s.plan.id, COUNT(DISTINCT s.user.id) FROM Subscription s
                        WHERE s.status = :st AND (s.expiresAt IS NULL OR s.expiresAt > :now)
                        GROUP BY s.plan.id
                        """, Object[].class)
                .setParameter("st", SubscriptionStatus.ACTIVE).setParameter("now", LocalDateTime.now())
                .getResultList()
                .forEach(r -> active.put((UUID) r[0], ((Number) r[1]).longValue()));
        return planRepository.findAllByOrderByPriceVndAsc().stream()
                .map(p -> toResponse(p, active.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Transactional
    public AdminPlanResponse updatePrice(UUID adminId, String code, UpdatePlanPriceRequest req) {
        SubscriptionPlan plan = planRepository.findByCode(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Plan not found."));
        if (!plan.isVip()) {
            throw new BusinessException(ErrorCode.CONFLICT, "The FREE plan has no price.");
        }
        long oldPrice = plan.getPriceVnd();
        String oldName = plan.getName();
        plan.setPriceVnd(req.priceVnd());
        if (req.name() != null && !req.name().isBlank()) plan.setName(req.name().trim());
        planRepository.save(plan);

        String reason = req.reason() == null || req.reason().isBlank() ? "" : " reason=" + req.reason().trim();
        auditLogService.log(AuditAction.ADMIN_PLAN_UPDATED, AuditResult.SUCCESS, adminId, null,
                "SubscriptionPlan", plan.getId().toString(),
                String.format(Locale.ROOT, "%s price %d->%d name '%s'->'%s'%s",
                        plan.getCode(), oldPrice, plan.getPriceVnd(), oldName, plan.getName(), reason));
        return toResponse(plan, 0);
    }

    private static AdminPlanResponse toResponse(SubscriptionPlan p, long activeSubscribers) {
        return AdminPlanResponse.builder()
                .id(p.getId()).code(p.getCode()).name(p.getName()).priceVnd(p.getPriceVnd())
                .durationDays(p.getDurationDays()).maxVaults(p.getMaxVaults())
                .maxBeneficiaries(p.getMaxBeneficiaries()).storageQuotaBytes(p.getStorageQuotaBytes())
                .legacyMessagesEnabled(p.isLegacyMessagesEnabled())
                .activeSubscribers(activeSubscribers)
                .build();
    }
}
