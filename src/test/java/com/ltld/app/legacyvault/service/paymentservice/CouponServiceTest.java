package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.coupondto.CouponPreviewResponse;
import com.ltld.app.legacyvault.dto.coupondto.CouponQuote;
import com.ltld.app.legacyvault.dto.coupondto.CreateCouponRequest;
import com.ltld.app.legacyvault.entity.Coupon;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.CouponRepository;
import com.ltld.app.legacyvault.repository.PaymentTransactionRepository;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Việc 4: mã giảm giá. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponServiceTest {

    @Mock private CouponRepository couponRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private AuditLogService auditLogService;

    private CouponService service;
    private final UUID userId = UUID.randomUUID();
    private SubscriptionPlan month;
    private SubscriptionPlan year;
    private SubscriptionPlan free;

    @BeforeEach
    void setUp() {
        service = new CouponService(couponRepository, planRepository, transactionRepository, auditLogService);
        free = SubscriptionPlan.builder().code("FREE").name("Free").priceVnd(0).build();
        month = SubscriptionPlan.builder().code("VIP_MONTH").name("VIP Monthly").priceVnd(99_000L).durationDays(30).build();
        year = SubscriptionPlan.builder().code("VIP_YEAR").name("VIP Yearly").priceVnd(990_000L).durationDays(365).build();
        when(planRepository.findAllByOrderByPriceVndAsc()).thenReturn(List.of(free, month, year));
    }

    private Coupon coupon(String code, int percent) {
        Coupon c = Coupon.builder().id(UUID.randomUUID()).code(code).percentOff(percent).build();
        when(couponRepository.findByCodeIgnoreCase(code)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void price_roundsToThousandAndKeepsMinimum() {
        assertThat(CouponService.price(Coupon.builder().percentOff(20).build(), month))
                .isEqualTo(new CouponQuote("VIP_MONTH", 99_000L, 20_000L, 79_000L));   // 79.200 -> 79.000
        assertThat(CouponService.price(Coupon.builder().percentOff(90).build(), month).finalVnd())
                .isEqualTo(10_000L);                                                 // 9.900 -> tối thiểu 10.000
        assertThat(CouponService.price(Coupon.builder().percentOff(15).build(), year).finalVnd())
                .isEqualTo(842_000L);                                                // 841.500 -> 842.000
    }

    @Test
    void quote_isCaseInsensitiveAndTrimmed() {
        coupon("TET2027", 20);
        CouponQuote q = service.quote(userId, "  tet2027 ", month);
        assertThat(q.finalVnd()).isEqualTo(79_000L);
    }

    @Test
    void quote_unknownInactiveOrExpired_isInvalid() {
        assertThatThrownBy(() -> service.quote(userId, "NOPE", month))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.COUPON_INVALID);

        coupon("OFF", 10).setActive(false);
        assertThatThrownBy(() -> service.quote(userId, "OFF", month))
                .extracting("errorCode").isEqualTo(ErrorCode.COUPON_INVALID);

        coupon("OLD", 10).setValidUntil(LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> service.quote(userId, "OLD", month))
                .extracting("errorCode").isEqualTo(ErrorCode.COUPON_INVALID);
    }

    @Test
    void quote_usedUp_orAlreadyUsedByThisUser() {
        Coupon c = coupon("LIMIT", 10);
        c.setMaxRedemptions(5);
        c.setRedeemedCount(5);
        assertThatThrownBy(() -> service.quote(userId, "LIMIT", month))
                .extracting("errorCode").isEqualTo(ErrorCode.COUPON_USED_UP);

        coupon("ONCE", 10);
        when(transactionRepository.existsByUserIdAndCouponCodeIgnoreCaseAndStatusIn(eq(userId), eq("ONCE"), any()))
                .thenReturn(true);
        assertThatThrownBy(() -> service.quote(userId, "ONCE", month))
                .extracting("errorCode").isEqualTo(ErrorCode.COUPON_ALREADY_USED);
    }

    @Test
    void quote_planSpecificCode_rejectsOtherPlan() {
        coupon("YEARONLY", 30).setPlanCode("VIP_YEAR");
        assertThatThrownBy(() -> service.quote(userId, "YEARONLY", month))
                .extracting("errorCode").isEqualTo(ErrorCode.COUPON_NOT_APPLICABLE);
        assertThat(service.quote(userId, "YEARONLY", year).finalVnd()).isEqualTo(693_000L);
    }

    @Test
    void preview_listsOnlyPaidVipPlansTheCodeAppliesTo() {
        coupon("ALL", 50);
        CouponPreviewResponse all = service.preview(userId, "all");
        assertThat(all.code()).isEqualTo("ALL");
        assertThat(all.plans()).extracting(CouponQuote::planCode).containsExactly("VIP_MONTH", "VIP_YEAR");

        coupon("YEARONLY", 30).setPlanCode("VIP_YEAR");
        assertThat(service.preview(userId, "YEARONLY").plans()).extracting(CouponQuote::planCode).containsExactly("VIP_YEAR");
    }

    @Test
    void create_normalizesCodeAndEndOfDay_andAudits() {
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> {
            Coupon c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        LocalDate until = LocalDate.now().plusDays(3);
        var res = service.create(userId, new CreateCouponRequest("tet-2027", 25, null, 100, until, " Tết "));

        assertThat(res.code()).isEqualTo("TET-2027");
        assertThat(res.validUntil()).isEqualTo(until.plusDays(1).atStartOfDay());
        assertThat(res.note()).isEqualTo("Tết");
        verify(auditLogService).log(eq(AuditAction.COUPON_CREATED), any(), eq(userId), any(), eq("Coupon"), anyString(), anyString());
    }

    @Test
    void create_duplicateOrPastDateOrFreePlan_rejected() {
        when(couponRepository.existsByCodeIgnoreCase("DUP")).thenReturn(true);
        assertThatThrownBy(() -> service.create(userId, new CreateCouponRequest("dup", 10, null, null, null, null)))
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);

        assertThatThrownBy(() -> service.create(userId,
                new CreateCouponRequest("PAST", 10, null, null, LocalDate.now().minusDays(1), null)))
                .extracting("errorCode").isEqualTo(ErrorCode.BAD_REQUEST);

        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(free));
        assertThatThrownBy(() -> service.create(userId, new CreateCouponRequest("FREEX", 10, "free", null, null, null)))
                .extracting("errorCode").isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void redeem_incrementsByNormalizedCode() {
        service.redeem(" tet2027 ");
        verify(couponRepository).incrementRedeemed("TET2027");
    }
}
