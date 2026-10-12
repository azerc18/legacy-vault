package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DmsReminderServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 9, 0);

    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private DmsReminderProcessor processor;
    @Mock private EmailSender emailSender;

    @InjectMocks
    private DmsReminderServiceImpl service;

    private static DmsReminderProcessor.ReminderMail mail(String email) {
        return new DmsReminderProcessor.ReminderMail(email, NOW, NOW.plusDays(7), 1, 3);
    }

    @Test
    @DisplayName("FR-03: một Owner lỗi (kể cả xung đột @Version) không chặn các Owner khác")
    void oneOwnerFailing_doesNotBlockOthers() {
        UUID failing = UUID.randomUUID();
        UUID crashing = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        when(dmsConfigRepository.findIdsDueForReminder(DmsStatus.ACTIVE, NOW)).thenReturn(List.of(failing, crashing, ok));
        when(processor.prepareReminder(failing, NOW))
                .thenThrow(new ObjectOptimisticLockingFailureException(DmsConfig.class, failing));
        when(processor.prepareReminder(crashing, NOW)).thenThrow(new IllegalStateException("boom"));
        when(processor.prepareReminder(ok, NOW)).thenReturn(Optional.of(mail("ok@example.test")));

        assertThatCode(() -> service.sendDueReminders(NOW)).doesNotThrowAnyException();

        verify(processor).prepareReminder(ok, NOW);
        verify(emailSender).sendDmsCheckinReminder("ok@example.test", NOW, NOW.plusDays(7), 1, 3);
        verify(emailSender, times(1)).sendDmsCheckinReminder(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("FR-03: gửi mail lỗi -> chỉ log, không ném, vẫn gửi cho Owner tiếp theo")
    void mailFailure_isSwallowed() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(dmsConfigRepository.findIdsDueForReminder(DmsStatus.ACTIVE, NOW)).thenReturn(List.of(first, second));
        when(processor.prepareReminder(first, NOW)).thenReturn(Optional.of(mail("first@example.test")));
        when(processor.prepareReminder(second, NOW)).thenReturn(Optional.of(mail("second@example.test")));
        doThrow(new IllegalStateException("SMTP down")).when(emailSender)
                .sendDmsCheckinReminder(eq("first@example.test"), any(), any(), anyInt(), anyInt());

        assertThatCode(() -> service.sendDueReminders(NOW)).doesNotThrowAnyException();

        verify(emailSender).sendDmsCheckinReminder(eq("second@example.test"), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("FR-03: mail chỉ gửi khi processor đã ghi nhận lần nhắc (sau khi processor trả về)")
    void mailSentOnlyAfterProcessorSucceeds() {
        UUID due = UUID.randomUUID();
        UUID skipped = UUID.randomUUID();
        when(dmsConfigRepository.findIdsDueForReminder(DmsStatus.ACTIVE, NOW)).thenReturn(List.of(skipped, due));
        when(processor.prepareReminder(skipped, NOW)).thenReturn(Optional.empty());
        when(processor.prepareReminder(due, NOW)).thenReturn(Optional.of(mail("due@example.test")));

        service.sendDueReminders(NOW);

        InOrder order = inOrder(processor, emailSender);
        order.verify(processor).prepareReminder(skipped, NOW);
        order.verify(processor).prepareReminder(due, NOW);
        order.verify(emailSender).sendDmsCheckinReminder("due@example.test", NOW, NOW.plusDays(7), 1, 3);
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("FR-03: không có DMS nào tới lần nhắc -> không gọi processor, không gửi mail")
    void nothingDue_doesNothing() {
        when(dmsConfigRepository.findIdsDueForReminder(DmsStatus.ACTIVE, NOW)).thenReturn(List.of());

        service.sendDueReminders(NOW);

        verifyNoInteractions(processor, emailSender);
    }
}
