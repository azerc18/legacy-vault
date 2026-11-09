package com.ltld.app.legacyvault.service.adminoverviewservice;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminChartsResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminUserDetailResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminVaultRowResponse;
import com.ltld.app.legacyvault.dto.auditdto.AdminAuditLogResponse;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.SecurityAlert;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.auditservice.AdminAuditLogServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationSummary;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetStatus;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A3 (chi tiết người dùng), B2 (tìm kho), C2 (biểu đồ). Chỉ đọc, gom bằng JPQL riêng của khu Admin
 * để không phải sửa repository dùng chung.
 */
@Service
@lombok.RequiredArgsConstructor
public class AdminInsightService {

    static final int RECENT_LOGS = 10;
    static final int RECENT_ALERTS = 10;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_EXPIRING_DAYS = 365;
    static final int REVENUE_DAYS = 30;
    static final int USER_WEEKS = 12;
    // Các luật cảnh báo theo sự kiện lấy subject = id audit log
    private static final EnumSet<AuditAction> ALERT_EVENT_ACTIONS =
            EnumSet.of(AuditAction.ACCOUNT_LOCKED, AuditAction.TOKEN_REUSE_DETECTED, AuditAction.RBAC_CHANGE);

    static final int MAX_ASSET_VERIFICATIONS = 100;

    private final com.ltld.app.legacyvault.service.assetverificationservice.AssetVerificationService assetVerificationService;

    @PersistenceContext
    private EntityManager em;

    /* ---------------- A3 ---------------- */

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(UUID userId) {
        User u = em.find(User.class, userId);
        if (u == null) throw new BusinessException(ErrorCode.NOT_FOUND, "User not found.");

        DmsConfig dms = em.createQuery("SELECT d FROM DmsConfig d WHERE d.owner.id = :id", DmsConfig.class)
                .setParameter("id", userId).getResultStream().findFirst().orElse(null);

        Subscription sub = em.createQuery("""
                        SELECT s FROM Subscription s JOIN FETCH s.plan
                        WHERE s.user.id = :id AND s.status = :status AND (s.expiresAt IS NULL OR s.expiresAt > :now)
                        ORDER BY s.expiresAt DESC
                        """, Subscription.class)
                .setParameter("id", userId).setParameter("status", SubscriptionStatus.ACTIVE)
                .setParameter("now", LocalDateTime.now())
                .getResultStream().filter(s -> s.getPlan().isVip()).findFirst().orElse(null);

        List<AdminUserDetailResponse.VaultItem> vaults = em.createQuery(
                        "SELECT v FROM Vault v WHERE v.owner.id = :id ORDER BY v.createdAt DESC", Vault.class)
                .setParameter("id", userId).setMaxResults(50).getResultList().stream()
                .map(v -> AdminUserDetailResponse.VaultItem.builder()
                        .id(v.getId()).name(v.getName()).status(v.getStatus().name())
                        .claimDeadlineAt(v.getClaimDeadlineAt()).createdAt(v.getCreatedAt()).build())
                .toList();

        // Nhật ký: việc người này làm hoặc việc làm lên người này (vd. bị khoá, được cấp quyền)
        List<AdminAuditLogResponse> logs = em.createQuery("""
                        SELECT l FROM AuditLog l LEFT JOIN FETCH l.actor a
                        WHERE a.id = :id OR l.entityId = :id
                        ORDER BY l.createdAt DESC
                        """, AuditLog.class)
                .setParameter("id", userId).setMaxResults(RECENT_LOGS).getResultList().stream()
                .map(AdminAuditLogServiceImpl::toResponse)
                .toList();

        return AdminUserDetailResponse.builder()
                .id(u.getId()).email(u.getEmail()).fullName(u.getFullName()).phoneNumber(u.getPhoneNumber())
                .status(u.getStatus() == null ? null : u.getStatus().name())
                .kycLevel(u.getKycLevel() == null ? null : u.getKycLevel().name())
                .mfaEnabled(u.isMfaEnabled()).failedLoginAttempts(u.getFailedLoginAttempts())
                .lockedUntil(u.getLockedUntil()).createdAt(u.getCreatedAt()).lastLoginAt(u.getLastLoginAt())
                .roles(u.getRoles().stream().map(Role::getCode).sorted().toList())
                .dmsStatus(dms == null || dms.getStatus() == null ? null : dms.getStatus().name())
                .dmsNextCheckinDueAt(dms == null ? null : dms.getNextCheckinDueAt())
                .planCode(sub == null ? "FREE" : sub.getPlan().getCode())
                .planName(sub == null ? null : sub.getPlan().getName())
                .planExpiresAt(sub == null ? null : sub.getExpiresAt())
                .vaults(vaults)
                .recentLogs(logs)
                .alerts(relatedAlerts(userId))
                .assetVerifications(assetVerifications(userId))
                .build();
    }

    // B.4: trạng thái hiệu lực (đã tính dấu vân tay, hết hạn) lấy từ chính service xác thực
    private List<AdminUserDetailResponse.AssetVerificationItem> assetVerifications(UUID userId) {
        List<DigitalAsset> assets = em.createQuery("""
                        SELECT a FROM DigitalAsset a JOIN FETCH a.vault v
                        WHERE v.owner.id = :id AND a.status = :status
                        ORDER BY a.createdAt DESC
                        """, DigitalAsset.class)
                .setParameter("id", userId).setParameter("status", AssetStatus.ACTIVE)
                .setMaxResults(MAX_ASSET_VERIFICATIONS).getResultList();
        Map<UUID, AssetVerificationSummary> summaries = assetVerificationService.summarize(assets);
        return assets.stream().map(a -> {
            AssetVerificationSummary s = summaries.get(a.getId());
            return AdminUserDetailResponse.AssetVerificationItem.builder()
                    .assetId(a.getId())
                    .assetType(a.getAssetType() == null ? null : a.getAssetType().name())
                    .vaultName(a.getVault().getName())
                    .status(s == null ? null : s.status().name())
                    .method(s == null || s.method() == null ? null : s.method().name())
                    .provider(s == null || s.provider() == null ? null : s.provider().name())
                    .verifiedAt(s == null ? null : s.verifiedAt())
                    .expiresAt(s == null ? null : s.expiresAt())
                    .build();
        }).toList();
    }

    // Cảnh báo có subject = id người dùng (tải hàng loạt) hoặc = id một audit log sự kiện của người này
    private List<AdminUserDetailResponse.AlertItem> relatedAlerts(UUID userId) {
        List<String> subjects = new ArrayList<>();
        subjects.add(userId.toString());
        em.createQuery("""
                        SELECT l.id FROM AuditLog l LEFT JOIN l.actor a
                        WHERE l.action IN :actions AND (a.id = :id OR l.entityId = :id)
                        ORDER BY l.createdAt DESC
                        """, UUID.class)
                .setParameter("actions", ALERT_EVENT_ACTIONS).setParameter("id", userId)
                .setMaxResults(200).getResultList()
                .forEach(id -> subjects.add(id.toString()));
        return em.createQuery("SELECT a FROM SecurityAlert a WHERE a.subject IN :subjects ORDER BY a.createdAt DESC",
                        SecurityAlert.class)
                .setParameter("subjects", subjects).setMaxResults(RECENT_ALERTS).getResultList().stream()
                .map(a -> AdminUserDetailResponse.AlertItem.builder()
                        .id(a.getId()).rule(a.getRule() == null ? null : a.getRule().name())
                        .severity(a.getSeverity() == null ? null : a.getSeverity().name())
                        .status(a.getStatus() == null ? null : a.getStatus().name())
                        .message(a.getMessage()).lastSeenAt(a.getLastSeenAt()).build())
                .toList();
    }

    /* ---------------- B2 ---------------- */

    /**
     * @param q                  tìm theo tên kho hoặc email / tên chủ kho (không phân biệt hoa thường)
     * @param expiringWithinDays chỉ lấy kho UNLOCKED có hạn nhận trong N ngày tới (bỏ qua status)
     */
    @Transactional(readOnly = true)
    public Page<AdminVaultRowResponse> searchVaults(String q, VaultStatus status, Integer expiringWithinDays,
                                                    int page, int size) {
        if (expiringWithinDays != null && (expiringWithinDays < 1 || expiringWithinDays > MAX_EXPIRING_DAYS)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "expiringWithinDays must be between 1 and 365.");
        }
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        boolean hasQ = q != null && !q.isBlank();
        LocalDateTime now = LocalDateTime.now();

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (hasQ) where.append(" AND (LOWER(v.name) LIKE :q OR LOWER(o.email) LIKE :q OR LOWER(o.fullName) LIKE :q)");
        if (expiringWithinDays != null) {
            where.append(" AND v.status = :unlocked AND v.claimDeadlineAt > :now AND v.claimDeadlineAt <= :until");
        } else if (status != null) {
            where.append(" AND v.status = :status");
        }
        String order = expiringWithinDays != null ? " ORDER BY v.claimDeadlineAt ASC" : " ORDER BY v.createdAt DESC, v.id DESC";

        TypedQuery<Long> count = em.createQuery("SELECT COUNT(v) FROM Vault v JOIN v.owner o" + where, Long.class);
        TypedQuery<Vault> rows = em.createQuery("SELECT v FROM Vault v JOIN FETCH v.owner o" + where + order, Vault.class);
        for (TypedQuery<?> tq : List.of(count, rows)) {
            if (hasQ) tq.setParameter("q", "%" + q.trim().toLowerCase(Locale.ROOT) + "%");
            if (expiringWithinDays != null) {
                tq.setParameter("unlocked", VaultStatus.UNLOCKED).setParameter("now", now)
                        .setParameter("until", now.plusDays(expiringWithinDays));
            } else if (status != null) {
                tq.setParameter("status", status);
            }
        }
        List<AdminVaultRowResponse> content = rows.setFirstResult(p * s).setMaxResults(s).getResultList().stream()
                .map(v -> AdminVaultRowResponse.builder()
                        .id(v.getId()).name(v.getName()).description(v.getDescription()).status(v.getStatus())
                        .ownerId(v.getOwner().getId()).ownerEmail(v.getOwner().getEmail())
                        .ownerName(v.getOwner().getFullName())
                        .unlockedAt(v.getUnlockedAt()).claimDeadlineAt(v.getClaimDeadlineAt())
                        .claimExtensions(v.getClaimExtensions())
                        .createdAt(v.getCreatedAt()).updatedAt(v.getUpdatedAt())
                        .build())
                .toList();
        return new PageImpl<>(content, PageRequest.of(p, s), count.getSingleResult());
    }

    /* ---------------- C2 ---------------- */

    @Transactional(readOnly = true)
    public AdminChartsResponse getCharts() {
        LocalDate today = LocalDate.now();

        // Doanh thu theo ngày thanh toán (giờ server, như paidAt), đủ 30 ngày kể cả ngày không có giao dịch
        LocalDate firstDay = today.minusDays(REVENUE_DAYS - 1L);
        Map<LocalDate, Long> revenue = new LinkedHashMap<>();
        for (LocalDate d = firstDay; !d.isAfter(today); d = d.plusDays(1)) revenue.put(d, 0L);
        em.createQuery("SELECT p.paidAt, p.amountVnd FROM PaymentTransaction p WHERE p.status = :st AND p.paidAt >= :from",
                        Object[].class)
                .setParameter("st", PaymentStatus.SUCCESS).setParameter("from", firstDay.atStartOfDay())
                .getResultList()
                .forEach(r -> revenue.computeIfPresent(((LocalDateTime) r[0]).toLocalDate(),
                        (k, v) -> v + ((Number) r[1]).longValue()));

        // Người dùng mới theo tuần (thứ Hai đầu tuần); createdAt là Instant -> đổi theo múi giờ server
        ZoneId zone = ZoneId.systemDefault();
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstMonday = thisMonday.minusWeeks(USER_WEEKS - 1L);
        Map<LocalDate, Long> users = new LinkedHashMap<>();
        for (LocalDate d = firstMonday; !d.isAfter(thisMonday); d = d.plusWeeks(1)) users.put(d, 0L);
        em.createQuery("SELECT u.createdAt FROM User u WHERE u.createdAt >= :from", Instant.class)
                .setParameter("from", firstMonday.atStartOfDay(zone).toInstant())
                .getResultList()
                .forEach(at -> users.computeIfPresent(
                        at.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        (k, v) -> v + 1));

        return AdminChartsResponse.builder()
                .revenueByDay(points(revenue))
                .newUsersByWeek(points(users))
                .build();
    }

    private static List<AdminChartsResponse.Point> points(Map<LocalDate, Long> m) {
        return m.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(e -> AdminChartsResponse.Point.builder().date(e.getKey()).value(e.getValue()).build())
                .toList();
    }
}
