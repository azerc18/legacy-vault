package com.ltld.app.legacyvault.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Đồng hồ hệ thống dùng chung; test thay bằng Clock.fixed để kiểm thời hạn OTP. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
