package com.ltld.app.legacyvault.integration;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Một container MySQL dùng chung cho mọi integration test (singleton container pattern).
 * Không dùng @Container: container sống suốt lần chạy test (Ryuk tự dọn khi JVM thoát),
 * nên context được Spring cache lại vẫn luôn trỏ tới container còn sống.
 */
public abstract class MySqlTestContainer {

    @ServiceConnection
    protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start();
    }
}