package com.ltld.app.legacyvault.dev;

import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/** CHỈ Ở PROFILE dev: không gửi mail thật, chỉ log; có công tắc giả lập lỗi gửi mail để test nhánh FAILED/retry. */
@Slf4j
@Service
@Profile("dev")
public class DevExecutorNotificationService implements ExecutorNotificationService {

    private final AtomicBoolean failing = new AtomicBoolean(false);

    @Override
    public void send(String recipientEmail, String content) {
        if (failing.get()) {
            throw new IllegalStateException("Simulated mail failure");
        }
        log.info("[DEV-MAIL] to={} content={}", recipientEmail, content);
    }

    public boolean isFailing() {
        return failing.get();
    }

    public void setFailing(boolean value) {
        failing.set(value);
    }
}
