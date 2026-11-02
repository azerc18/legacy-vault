package com.ltld.app.legacyvault.service.fileservice;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * H1: MinIO cho integration test. Docker Hub đã gỡ image minio/minio, nên dùng bản đóng gói đã đóng băng của
 * Bitnami (cùng image với docker-compose). Lệnh khởi động khác bản gốc nên không dùng MinIOContainer.
 */
final class S3TestContainers {

    static final String IMAGE = "bitnamilegacy/minio:2025.4.22";
    static final String USER = "lv-test-user";
    static final String PASSWORD = "lv-test-secret";
    private static final int API_PORT = 9000;

    private S3TestContainers() {
    }

    static GenericContainer<?> minio() {
        return new GenericContainer<>(IMAGE)
                .withEnv("MINIO_ROOT_USER", USER)
                .withEnv("MINIO_ROOT_PASSWORD", PASSWORD)
                .withExposedPorts(API_PORT)
                .waitingFor(Wait.forHttp("/minio/health/live").forPort(API_PORT));
    }

    static S3FileStorageService storage(GenericContainer<?> minio, String bucket) {
        String endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(API_PORT);
        return new S3FileStorageService(endpoint, "us-east-1", bucket, USER, PASSWORD, true);
    }
}
