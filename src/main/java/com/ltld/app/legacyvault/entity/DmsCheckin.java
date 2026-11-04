package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dms_checkins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DmsCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dms_config_id", nullable = false)
    private DmsConfig dmsConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkin_type", nullable = false)
    private DmsCheckinType checkinType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NotificationChannel channel;

    // F2: jti của one-time check-in JWT token (chỉ dùng 1 lần)
    @Column(name = "token_jti", length = 100, unique = true)
    private String tokenJti;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;
}
