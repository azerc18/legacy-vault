package com.ltld.app.legacyvault.service.fileservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** H1: app.storage.provider chọn đúng một bản FileStorageService. */
class FileStorageProviderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FileStorageServiceImpl.class, S3FileStorageService.class);

    @Test
    @DisplayName("H1: không cấu hình provider -> dùng đĩa local như trước")
    void defaultsToLocal() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(FileStorageService.class)
                .getBean(FileStorageService.class).isInstanceOf(FileStorageServiceImpl.class));
    }

    @Test
    @DisplayName("H1: provider=s3 -> chỉ có S3FileStorageService")
    void s3WhenConfigured() {
        runner.withPropertyValues("app.storage.provider=s3", "app.storage.s3.endpoint=http://127.0.0.1:1",
                        "app.storage.s3.bucket=b", "app.storage.s3.access-key=a", "app.storage.s3.secret-key=s")
                .run(ctx -> assertThat(ctx).hasSingleBean(FileStorageService.class)
                        .getBean(FileStorageService.class).isInstanceOf(S3FileStorageService.class));
    }
}
