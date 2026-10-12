package com.ltld.app.legacyvault.service.dmsservice;

import java.time.LocalDateTime;

public interface DmsReminderService {

    /** Gửi nhắc check-in cho các DMS ACTIVE đã tới lần nhắc kế tiếp (FR-03 bước 5). */
    void sendDueReminders(LocalDateTime now);
}
