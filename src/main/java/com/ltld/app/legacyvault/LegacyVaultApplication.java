package com.ltld.app.legacyvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LegacyVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegacyVaultApplication.class, args);
    }

}
