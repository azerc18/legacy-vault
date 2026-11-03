package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.DmsConfirmerStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "dms_confirmers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dms_confirmer_config_email",
                columnNames = {"dms_config_id", "email"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DmsConfirmer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dms_config_id", nullable = false)
    private DmsConfig dmsConfig;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 80)
    private String relationship;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DmsConfirmerStatus status = DmsConfirmerStatus.ACTIVE;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "last_token_jti", length = 100, unique = true)
    private String lastTokenJti;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

