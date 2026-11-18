package com.ltld.app.legacyvault.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cờ tích hợp Admin chỉnh ở trang "Tích hợp". Chỉ có 1 dòng (id = {@link #SINGLETON_ID}). Không chứa key / secret.
 * Cột NULL = theo biến môi trường như trước khi có trang này.
 */
@Entity
@Table(name = "integration_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationSetting {

    public static final byte SINGLETON_ID = 1;

    @Id
    private Byte id;

    /** eKYC mô phỏng cho người thụ hưởng. NULL = theo app.kyc.mock.enabled (APP_KYC_MOCK_ENABLED). */
    @Column(name = "kyc_mock_enabled")
    private Boolean kycMockEnabled;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
