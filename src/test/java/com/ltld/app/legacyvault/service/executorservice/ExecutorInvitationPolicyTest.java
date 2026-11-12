package com.ltld.app.legacyvault.service.executorservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Luật hạn lời mời: cấu hình sai phải báo ngay lúc khởi động, không để chạy sai trên môi trường thật. */
class ExecutorInvitationPolicyTest {

    @Test
    @DisplayName("Nhắc trước >= hạn trả lời: báo lỗi lúc tạo bean (nếu không sẽ nhắc ngay lúc vừa mời)")
    void reminderNotShorterThanExpiry_rejected() {
        assertThatThrownBy(() -> new ExecutorInvitationPolicy(7, 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invitation-reminder-days");
        assertThatThrownBy(() -> new ExecutorInvitationPolicy(3, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invitation-reminder-days");
    }

    @Test
    @DisplayName("Số ngày không hợp lệ: báo lỗi")
    void nonPositiveDays_rejected() {
        assertThatThrownBy(() -> new ExecutorInvitationPolicy(0, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invitation-expiry-days");
        assertThatThrownBy(() -> new ExecutorInvitationPolicy(7, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invitation-reminder-days");
    }

    @Test
    @DisplayName("Cấu hình mặc định (7 / 3) hợp lệ")
    void defaults_accepted() {
        assertThatCode(() -> new ExecutorInvitationPolicy(7, 3)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("daysLeft làm tròn lên, tối thiểu 1")
    void daysLeft_roundsUp() {
        ExecutorInvitationPolicy policy = new ExecutorInvitationPolicy(7, 3);
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 10, 0);

        assertThat(policy.daysLeft(now.plusHours(49), now)).isEqualTo(3);
        assertThat(policy.daysLeft(now.plusHours(48), now)).isEqualTo(2);
        assertThat(policy.daysLeft(now.plusMinutes(5), now)).isEqualTo(1);
        assertThat(policy.expiresAt(now)).isEqualTo(now.plusDays(7));
    }
}
