package com.ltld.app.legacyvault.service.messageservice;

import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyMessageDeliverySchedulerTest {

    @Mock
    private LegacyMessageService legacyMessageService;

    @InjectMocks
    private LegacyMessageDeliveryScheduler scheduler;

    @InjectMocks
    private LegacyMessageUnlockListener unlockListener;

    @Test
    @DisplayName("F4b: Scheduler quét và gửi các lời nhắn hẹn giờ đến hạn")
    void deliverDueMessages_DelegatesToService() {
        LocalDate today = LocalDate.of(2026, 11, 3);
        when(legacyMessageService.deliverDueScheduledMessages(today)).thenReturn(2);

        int count = scheduler.deliverDueMessages(today);

        assertThat(count).isEqualTo(2);
        verify(legacyMessageService).deliverDueScheduledMessages(today);
    }

    @Test
    @DisplayName("F4b: Listener sự kiện VaultsUnlockedEvent kích hoạt phát lời nhắn ON_CLAIM cho từng Vault")
    void onVaultsUnlocked_DeliversOnClaimMessagesForEachVault() {
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        VaultsUnlockedEvent event = new VaultsUnlockedEvent(UUID.randomUUID(), List.of(v1, v2));

        unlockListener.onVaultsUnlocked(event);

        verify(legacyMessageService).deliverOnClaimMessages(v1);
        verify(legacyMessageService).deliverOnClaimMessages(v2);
    }
}

