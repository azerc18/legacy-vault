package com.ltld.app.legacyvault.service.adminoverviewservice;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminStatsResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.SecurityAlert;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.enums.SecurityAlertSeverity;
import com.ltld.app.legacyvault.enums.SecurityAlertStatus;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.service.dmsservice.ExecutorRescueRule;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A1: số liệu cho trang tổng quan Admin. Chỉ đọc, gom bằng JPQL đếm theo nhóm
 * (không sửa repository dùng chung để tránh đụng file của nhóm khác).
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    static final int CLAIM_DEADLINE_DAYS = 7;          // vault UNLOCKED sắp hết hạn nhận trong 7 ngày
    static final int STALE_PENDING_MINUTES = 30;       // giao dịch PENDING quá 30 phút coi là treo
    static final int TODO_LIMIT = 10;

    @PersistenceContext
    private EntityManager em;

    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final LegalVerificationRequestRepository legalVerificationRequestRepository;

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        LocalDateTime now = LocalDateTime.now();
        // paidAt được ghi bằng LocalDateTime.now() của server -> dùng cùng đồng hồ đó
        LocalDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();

        Object[] revenue = em.createQuery("""
                        SELECT COALESCE(SUM(p.amountVnd), 0), COUNT(p) FROM PaymentTransaction p
                        WHERE p.status = :status AND p.paidAt >= :from
                        """, Object[].class)
                .setParameter("status", PaymentStatus.SUCCESS)
                .setParameter("from", monthStart)
                .getSingleResult();

        Long vipUsers = em.createQuery("""
                        SELECT COUNT(DISTINCT s.user.id) FROM Subscription s
                        WHERE s.status = :status AND UPPER(s.plan.code) <> 'FREE'
                          AND (s.expiresAt IS NULL OR s.expiresAt > :now)
                        """, Long.class)
                .setParameter("status", SubscriptionStatus.ACTIVE)
                .setParameter("now", now)
                .getSingleResult();

        Map<String, Long> dms = countBy("SELECT d.status, COUNT(d) FROM DmsConfig d GROUP BY d.status", DmsStatus.values());

        return AdminStatsResponse.builder()
                .usersByStatus(countBy("SELECT u.status, COUNT(u) FROM User u GROUP BY u.status", UserStatus.values()))
                .vaultsByStatus(countBy("SELECT v.status, COUNT(v) FROM Vault v GROUP BY v.status", VaultStatus.values()))
                .openAlerts(countAlerts(null))
                .openHighAlerts(countAlerts(SecurityAlertSeverity.HIGH))
                .dmsPendingConfirmation(dms.get(DmsStatus.PENDING_CONFIRMATION.name()))
                .dmsTriggered(dms.get(DmsStatus.TRIGGERED.name()))
                .executorRescueNeeded(countExecutorRescue(now))
                .revenueThisMonthVnd(((Number) revenue[0]).longValue())
                .successfulPaymentsThisMonth(((Number) revenue[1]).longValue())
                .vipUsers(vipUsers)
                .highAlerts(highAlerts())
                .claimDeadlines(claimDeadlines(now))
                .stalePendingPayments(stalePendingPayments(now))
                .build();
    }

    private Map<String, Long> countBy(String jpql, Enum<?>[] all) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Enum<?> e : all) out.put(e.name(), 0L);
        for (Object[] row : em.createQuery(jpql, Object[].class).getResultList()) {
            if (row[0] != null) out.put(((Enum<?>) row[0]).name(), ((Number) row[1]).longValue());
        }
        return out;
    }

    private long countAlerts(SecurityAlertSeverity severity) {
        return em.createQuery("""
                        SELECT COUNT(a) FROM SecurityAlert a
                        WHERE a.status = :status AND (:severity IS NULL OR a.severity = :severity)
                        """, Long.class)
                .setParameter("status", SecurityAlertStatus.OPEN)
                .setParameter("severity", severity)
                .getSingleResult();
    }

    // Cùng quy tắc với cờ executorRescueNeeded ở danh sách user (ExecutorRescueRule)
    private long countExecutorRescue(LocalDateTime now) {
        List<DmsConfig> triggered = em.createQuery(
                        "SELECT d FROM DmsConfig d JOIN FETCH d.owner WHERE d.status = :status", DmsConfig.class)
                .setParameter("status", DmsStatus.TRIGGERED)
                .getResultList();
        if (triggered.isEmpty()) return 0;
        List<UUID> ownerIds = triggered.stream().map(d -> d.getOwner().getId()).toList();
        Map<UUID, List<ExecutorAssignment>> byOwner = executorAssignmentRepository.findActiveWithUserByOwnerIdIn(ownerIds)
                .stream().collect(Collectors.groupingBy(a -> a.getOwner().getId()));
        return triggered.stream().filter(d -> {
            UUID ownerId = d.getOwner().getId();
            return ExecutorRescueRule.rescueNeeded(d, byOwner.getOrDefault(ownerId, List.of()), now,
                    a -> legalVerificationRequestRepository.existsByOwnerIdAndExecutorIdAndCreatedAtAfter(
                            ownerId, a.getExecutorUser().getId(), a.getActivatedAt()));
        }).count();
    }

    private List<AdminStatsResponse.AlertItem> highAlerts() {
        return em.createQuery("""
                        SELECT a FROM SecurityAlert a
                        WHERE a.status = :status AND a.severity = :severity
                        ORDER BY a.lastSeenAt DESC
                        """, SecurityAlert.class)
                .setParameter("status", SecurityAlertStatus.OPEN)
                .setParameter("severity", SecurityAlertSeverity.HIGH)
                .setMaxResults(TODO_LIMIT)
                .getResultList().stream()
                .map(a -> AdminStatsResponse.AlertItem.builder()
                        .id(a.getId()).rule(a.getRule() == null ? null : a.getRule().name())
                        .subject(a.getSubject()).message(a.getMessage())
                        .eventCount(a.getEventCount()).lastSeenAt(a.getLastSeenAt())
                        .build())
                .toList();
    }

    private List<AdminStatsResponse.VaultDeadlineItem> claimDeadlines(LocalDateTime now) {
        return em.createQuery("""
                        SELECT v FROM Vault v JOIN FETCH v.owner
                        WHERE v.status = :status AND v.claimDeadlineAt > :now AND v.claimDeadlineAt <= :until
                        ORDER BY v.claimDeadlineAt ASC
                        """, Vault.class)
                .setParameter("status", VaultStatus.UNLOCKED)
                .setParameter("now", now)
                .setParameter("until", now.plusDays(CLAIM_DEADLINE_DAYS))
                .setMaxResults(TODO_LIMIT)
                .getResultList().stream()
                .map(v -> AdminStatsResponse.VaultDeadlineItem.builder()
                        .id(v.getId()).name(v.getName())
                        .ownerId(v.getOwner().getId()).ownerEmail(v.getOwner().getEmail())
                        .claimDeadlineAt(v.getClaimDeadlineAt())
                        .build())
                .toList();
    }

    private List<AdminStatsResponse.PendingPaymentItem> stalePendingPayments(LocalDateTime now) {
        return em.createQuery("""
                        SELECT p FROM PaymentTransaction p JOIN FETCH p.user JOIN FETCH p.plan
                        WHERE p.status = :status AND p.createdAt < :before
                        ORDER BY p.createdAt ASC
                        """, PaymentTransaction.class)
                .setParameter("status", PaymentStatus.PENDING)
                .setParameter("before", now.minusMinutes(STALE_PENDING_MINUTES))
                .setMaxResults(TODO_LIMIT)
                .getResultList().stream()
                .map(p -> AdminStatsResponse.PendingPaymentItem.builder()
                        .id(p.getId()).userId(p.getUser().getId()).userEmail(p.getUser().getEmail())
                        .planCode(p.getPlan().getCode()).amountVnd(p.getAmountVnd()).createdAt(p.getCreatedAt())
                        .build())
                .toList();
    }
}
