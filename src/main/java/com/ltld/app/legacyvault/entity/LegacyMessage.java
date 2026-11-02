package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.LegacyMessageDeliveryTrigger;
import com.ltld.app.legacyvault.enums.LegacyMessageRecipientType;
import com.ltld.app.legacyvault.enums.LegacyMessageStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "legacy_messages",
        indexes = {
                @Index(name = "idx_legacy_msg_vault", columnList = "vault_id"),
                @Index(name = "idx_legacy_msg_recipient", columnList = "recipient_beneficiary_id"),
                @Index(name = "idx_legacy_msg_schedule", columnList = "status, delivery_trigger, deliver_date")
        })
public class LegacyMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id", nullable = false)
    private Vault vault;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false)
    @Builder.Default
    private LegacyMessageRecipientType recipientType = LegacyMessageRecipientType.ALL_BENEFICIARIES;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_beneficiary_id")
    private VaultBeneficiary recipientBeneficiary;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_trigger", nullable = false)
    @Builder.Default
    private LegacyMessageDeliveryTrigger deliveryTrigger = LegacyMessageDeliveryTrigger.ON_CLAIM;

    @Column(name = "deliver_date")
    private LocalDate deliverDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LegacyMessageStatus status = LegacyMessageStatus.ACTIVE;

    @Column(name = "attachment_name")
    private String attachmentName;

    @Column(name = "attachment_path", length = 512)
    private String attachmentPath;

    @Column(name = "attachment_type", length = 100)
    private String attachmentType;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
}

