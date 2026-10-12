package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DmsReminderServiceImpl implements DmsReminderService {

    private final DmsConfigRepository dmsConfigRepository;
    private final DmsReminderProcessor processor;
    private final EmailSender emailSender;

    // Không @Transactional ở đây: mỗi Owner chạy trong transaction riêng của processor và đã commit
    // khi prepareReminder trả về, nên mail chỉ được gửi cho lần nhắc đã ghi nhận thành công.
    @Override
    public void sendDueReminders(LocalDateTime now) {
        for (UUID configId : dmsConfigRepository.findIdsDueForReminder(DmsStatus.ACTIVE, now)) {
            Optional<DmsReminderProcessor.ReminderMail> mail;
            try {
                mail = processor.prepareReminder(configId, now);
            } catch (RuntimeException e) {
                // Kể cả xung đột @Version (Owner check-in cùng lúc): bỏ qua Owner này, lượt quét sau xét lại.
                log.error("FR-03: preparing reminder failed for dmsConfig {}: {}", configId, e.getMessage(), e);
                continue;
            }
            mail.ifPresent(m -> send(configId, m));
        }
    }

    // Nhắc nhở gửi tối đa một lần: lỗi mail chỉ log, không gửi lại.
    private void send(UUID configId, DmsReminderProcessor.ReminderMail mail) {
        try {
            emailSender.sendDmsCheckinReminder(mail.ownerEmail(), mail.checkinDueAt(), mail.triggerAt(),
                    mail.reminderNumber(), mail.reminderCount());
        } catch (RuntimeException e) {
            log.warn("FR-03: sending reminder {}/{} for dmsConfig {} failed: {}",
                    mail.reminderNumber(), mail.reminderCount(), configId, e.getMessage());
        }
    }
}
