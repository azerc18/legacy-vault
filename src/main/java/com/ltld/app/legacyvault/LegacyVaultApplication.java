package com.ltld.app.legacyvault;

import com.ltld.app.legacyvault.config.AppTimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LegacyVaultApplication {

    public static void main(String[] args) {
        // Giờ VN cho toàn ứng dụng (Azure chạy UTC); DB vẫn lưu UTC, xem AppTimeZone
        AppTimeZone.applyDefault();
        SpringApplication.run(LegacyVaultApplication.class, args);
    }

}
