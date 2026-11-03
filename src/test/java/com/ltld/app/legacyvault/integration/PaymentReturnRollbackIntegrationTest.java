package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.repository.PaymentTransactionRepository;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.paymentservice.PaymentService;
import com.ltld.app.legacyvault.service.paymentservice.VnpayClient;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * T1 (n6): bật activate-on-return mà bước tạo gói lỗi thì cả transaction rollback,
 * không để đơn SUCCESS mà không có gói (IPN gọi sau vẫn xử lý lại được đơn PENDING).
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
        "app.beneficiary.claim-expiry.enabled=false",
        "app.vnpay.tmn-code=TESTTMN",
        "app.vnpay.hash-secret=SECRET_KEY_FOR_TESTING_1234567890",
        "app.vnpay.activate-on-return=true"
})
class PaymentReturnRollbackIntegrationTest extends MySqlTestContainer {

    @MockitoBean
    private EmailSender emailSender;

    @MockitoSpyBean
    private SubscriptionRepository subscriptionRepository;

    @Autowired private PaymentService paymentService;
    @Autowired private VnpayClient vnpayClient;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM subscriptions");
        jdbcTemplate.update("DELETE FROM payment_transactions");
        jdbcTemplate.update("DELETE FROM subscription_plans WHERE code = 'VIP_N6_TEST'");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    @DisplayName("n6: activate-on-return lỗi khi tạo gói thì đơn vẫn PENDING, redirect failed")
    void activateOnReturn_subscriptionSaveFails_rollsBackTransaction() {
        User owner = userRepository.save(User.builder()
                .email("owner-n6@example.com")
                .fullName("Owner N6")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .build());
        SubscriptionPlan plan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .code("VIP_N6_TEST")
                .name("VIP n6 test")
                .priceVnd(99_000L)
                .durationDays(30)
                .maxVaults(5)
                .maxBeneficiaries(10)
                .storageQuotaBytes(5_368_709_120L)
                .legacyMessagesEnabled(true)
                .build());
        PaymentTransaction tx = paymentTransactionRepository.save(PaymentTransaction.builder()
                .user(owner)
                .plan(plan)
                .amountVnd(99_000L)
                .status(PaymentStatus.PENDING)
                .build());

        doThrow(new IllegalStateException("simulated failure")).when(subscriptionRepository).save(any(Subscription.class));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", tx.getId().toString());
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        String redirectUrl = paymentService.handleVnpayReturn(params);

        assertThat(redirectUrl).contains("status=failed");
        PaymentTransaction reloaded = paymentTransactionRepository.findById(tx.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(reloaded.getPaidAt()).isNull();
    }
}
