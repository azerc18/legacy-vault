package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.AiFallbackProvider;
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

    // AI dự phòng khi Gemini lỗi (Groq / DeepSeek). Provider / model NULL = mặc định của biến môi trường.
    @Column(name = "fallback_enabled", nullable = false)
    @Builder.Default
    private boolean fallbackEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "text_fallback_provider", length = 20)
    private AiFallbackProvider textFallbackProvider;

    @Column(name = "text_fallback_model", length = 100)
    private String textFallbackModel;

    @Column(name = "vision_fallback_model", length = 100)
    private String visionFallbackModel;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
