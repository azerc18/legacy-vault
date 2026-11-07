package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.PaymentReceiptData;
import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReceiptServiceTest {

    @Mock private PaymentTransactionRepository paymentTransactionRepository;

    private PaymentReceiptService service;
    private User owner;
    private SubscriptionPlan plan;

    @BeforeEach
    void setUp() {
        service = new PaymentReceiptService(paymentTransactionRepository);
        owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").fullName("Nguyễn Văn Ông").build();
        plan = SubscriptionPlan.builder().id(UUID.randomUUID()).code("VIP_MONTH").name("VIP Monthly")
                .priceVnd(99_000L).durationDays(30).build();
    }

    private PaymentTransaction tx(PaymentStatus status) {
        return PaymentTransaction.builder()
                .id(UUID.fromString("1a2b3c4d-0000-0000-0000-000000000001"))
                .user(owner).plan(plan).amountVnd(99_000L).status(status)
                .vnpTransactionNo("14512345").vnpBankCode("NCB")
                .createdAt(LocalDateTime.of(2026, 11, 7, 9, 55))
                .paidAt(status == PaymentStatus.SUCCESS ? LocalDateTime.of(2026, 11, 7, 10, 0) : null)
                .build();
    }

    @Test
    void successfulOwnPayment_returnsReceiptData() {
        PaymentTransaction tx = tx(PaymentStatus.SUCCESS);
        when(paymentTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));

        PaymentReceiptData data = service.getReceiptData(tx.getId(), owner.getId());

        assertThat(data.receiptNo()).isEqualTo("LV-20261107-1A2B3C4D");
        assertThat(data.customerEmail()).isEqualTo("owner@example.com");
        assertThat(data.planCode()).isEqualTo("VIP_MONTH");
        assertThat(data.durationDays()).isEqualTo(30);
        assertThat(data.amountVnd()).isEqualTo(99_000L);
        assertThat(data.vnpTransactionNo()).isEqualTo("14512345");
        assertThat(data.issuedAt()).isNotNull();
    }

    @Test
    void otherUsersPayment_isNotFound() {
        PaymentTransaction tx = tx(PaymentStatus.SUCCESS);
        when(paymentTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> service.getReceiptData(tx.getId(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void missingPayment_isNotFound() {
        UUID id = UUID.randomUUID();
        when(paymentTransactionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReceiptData(id, owner.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void pendingOrFailedPayment_hasNoReceipt() {
        for (PaymentStatus status : new PaymentStatus[]{PaymentStatus.PENDING, PaymentStatus.FAILED}) {
            PaymentTransaction tx = tx(status);
            when(paymentTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));

            assertThatThrownBy(() -> service.getReceiptData(tx.getId(), owner.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        }
    }
}
