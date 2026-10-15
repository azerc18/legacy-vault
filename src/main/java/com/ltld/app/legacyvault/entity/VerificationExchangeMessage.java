package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.ExchangeSenderRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "verification_exchange_messages",
        indexes = @Index(name = "idx_exchange_request_created", columnList = "request_id, created_at")
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VerificationExchangeMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private LegalVerificationRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false)
    private ExchangeSenderRole senderRole;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "attachment_path", length = 500)
    private String attachmentPath;

    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
