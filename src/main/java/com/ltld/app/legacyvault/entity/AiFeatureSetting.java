package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.AiFeature;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cấu hình một chức năng AI do Admin chỉnh. Không chứa API key (key chỉ ở biến môi trường).
 * Model NULL = dùng model mặc định của biến môi trường.
 */
@Entity
@Table(name = "ai_feature_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiFeatureSetting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private AiFeature feature;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "primary_model", length = 100)
    private String primaryModel;

    @Column(name = "backup_model", length = 100)
    private String backupModel;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
