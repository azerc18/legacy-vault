package com.ltld.app.legacyvault.service.fileservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** H1: các nhánh khởi động không tái hiện được bằng MinIO thật. */
class S3FileStorageServiceTest {

    @Test
    @DisplayName("H1: bucket chưa có và khóa không được tạo bucket (token R2 chỉ có quyền object) -> app vẫn khởi động")
    @SuppressWarnings("unchecked")
    void ensureBucket_createDenied_doesNotFailStartup() {
        S3Client s3 = mock(S3Client.class);
        when(s3.headBucket(any(Consumer.class))).thenThrow(NoSuchBucketException.builder().build());
        when(s3.createBucket(any(Consumer.class))).thenThrow(S3Exception.builder().statusCode(403).message("denied").build());

        assertThatCode(() -> new S3FileStorageService(s3, "legacyvault").ensureBucket()).doesNotThrowAnyException();
        verify(s3).createBucket(any(Consumer.class));
    }
}
