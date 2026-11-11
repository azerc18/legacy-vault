package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPaymentFilter;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPaymentResponse;
import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.repository.PaymentTransactionRepository;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminPaymentService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review #256 nit: trang Thanh toán của admin đánh dấu giao dịch mà tài khoản đã trả từ hai lần trở lên với cùng mã
 * (lần sau đã ghi audit COUPON_REUSED). Cùng cấu hình với MailQueriesIntegrationTest để dùng chung Spring context.
 */
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create",
        "app.dms.scheduler.enabled=false",
        "app.beneficiary.notify-retry.enabled=false",
        "app.beneficiary.vault-purge.enabled=false",
        "app.beneficiary.claim-expiry.enabled=false"
})
class AdminPaymentCouponReusedIntegrationTest extends MySqlTestContainer {

    @MockitoBean private EmailSender emailSender;

    @Autowired private AdminPaymentService adminPaymentService;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionPlanRepository planRepository;
    @Autowired private PaymentTransactionRepository transactionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<UUID> users = new ArrayList<>();
    private SubscriptionPlan plan;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM payment_transactions");
        jdbcTemplate.update("DELETE FROM subscription_plans WHERE code = 'VIP_REUSE_TEST'");
        for (UUID id : users) {
            jdbcTemplate.update("DELETE FROM users WHERE BIN_TO_UUID(id) = ?", id.toString());
        }
        users.clear();
    }

    private User user(String prefix) {
        User u = userRepository.save(User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com").fullName(prefix)
                .passwordHash("not-used").status(UserStatus.ACTIVE).build());
        users.add(u.getId());
        return u;
    }

    private PaymentTransaction tx(User u, String code, PaymentStatus status) {
        return transactionRepository.save(PaymentTransaction.builder()
                .user(u).plan(plan).amountVnd(79_000L).couponCode(code).discountVnd(20_000L).status(status).build());
    }

    @Test
    void secondPaidOrderWithSameCode_isFlaggedOnBothRows_otherCasesAreNot() {
        plan = planRepository.save(SubscriptionPlan.builder().code("VIP_REUSE_TEST").name("VIP reuse test")
                .priceVnd(99_000L).durationDays(30).maxVaults(5).maxBeneficiaries(10).build());
        User twice = user("twice");
        User once = user("once");
        PaymentTransaction first = tx(twice, "TET2027", PaymentStatus.SUCCESS);
        PaymentTransaction second = tx(twice, "tet2027", PaymentStatus.REFUNDED);
        PaymentTransaction expired = tx(twice, "TET2027", PaymentStatus.EXPIRED);
        PaymentTransaction single = tx(once, "TET2027", PaymentStatus.SUCCESS);
        PaymentTransaction otherCode = tx(once, "SUMMER", PaymentStatus.SUCCESS);

        Map<UUID, AdminPaymentResponse> byId = adminPaymentService
                .export(new AdminPaymentFilter(null, null, null, null))
                .stream().collect(Collectors.toMap(AdminPaymentResponse::getId, Function.identity()));

        assertThat(byId.get(first.getId()).isCouponReused()).isTrue();
        assertThat(byId.get(second.getId()).isCouponReused()).isTrue();
        assertThat(byId.get(expired.getId()).isCouponReused()).isFalse();
        assertThat(byId.get(single.getId()).isCouponReused()).isFalse();
        assertThat(byId.get(otherCode.getId()).isCouponReused()).isFalse();
    }
}
