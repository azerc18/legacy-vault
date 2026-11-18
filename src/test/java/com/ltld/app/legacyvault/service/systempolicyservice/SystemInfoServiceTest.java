package com.ltld.app.legacyvault.service.systempolicyservice;

import com.ltld.app.legacyvault.dto.systempolicydto.SystemInfoResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Thông tin chỉ đọc: chỉ nói đã cấu hình hay chưa, không bao giờ trả giá trị khoá. */
class SystemInfoServiceTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<Flyway> noFlyway() {
        ObjectProvider<Flyway> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(null);
        return p;
    }

    @Test
    @DisplayName("S3 + khoá riêng + khai báo sao lưu -> trả đúng, không lộ giá trị khoá")
    void s3_withBackup() {
        SystemInfoService s = new SystemInfoService(noFlyway(), "bWFzdGVyLWtleS1zZWNyZXQ=", "b3RwLWtleQ==", "",
                "S3", "legacyvault", "Azure Database for MySQL", "7", "Sao lưu tự động mỗi ngày");

        SystemInfoResponse r = s.get();

        assertThat(r.crypto().dataEncryption()).isEqualTo("AES-256-GCM");
        assertThat(r.crypto().masterKeyConfigured()).isTrue();
        assertThat(r.crypto().otpKeySeparate()).isTrue();
        assertThat(r.crypto().identityKeySeparate()).isFalse();
        assertThat(r.storage().provider()).isEqualTo("s3");
        assertThat(r.storage().bucket()).isEqualTo("legacyvault");
        assertThat(r.backup().provider()).isEqualTo("Azure Database for MySQL");
        assertThat(r.backup().retentionDays()).isEqualTo(7);
        assertThat(r.schemaVersion()).isNull();
        assertThat(r.toString()).doesNotContain("bWFzdGVyLWtleS1zZWNyZXQ=").doesNotContain("b3RwLWtleQ==");
    }

    @Test
    @DisplayName("Local, chưa khai báo sao lưu, số ngày sai -> bucket null, backup để trống")
    void local_noBackup() {
        SystemInfoService s = new SystemInfoService(noFlyway(), "", "", "", "local", "legacyvault", "", "abc", " ");

        SystemInfoResponse r = s.get();

        assertThat(r.crypto().masterKeyConfigured()).isFalse();
        assertThat(r.storage().provider()).isEqualTo("local");
        assertThat(r.storage().bucket()).isNull();
        assertThat(r.backup().provider()).isNull();
        assertThat(r.backup().retentionDays()).isNull();
        assertThat(r.backup().note()).isNull();
    }
}
