package com.ltld.app.legacyvault.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** OTP xác minh số điện thoại của một người dùng; gửi lại thì ghi đè dòng cũ. */
@Entity
@Table(name = "phone_otp_challenges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhoneOtpChallenge {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "phone_e164", nullable = false, length = 16)
    private String phoneE164;

    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(nullable = false)
    private int attempts;

    // Cửa sổ đếm số lần gửi (giới hạn số SMS mỗi giờ)
    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "window_send_count", nullable = false)
    private int windowSendCount;
}
