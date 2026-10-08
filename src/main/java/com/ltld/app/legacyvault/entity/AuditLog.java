package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.ActionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Người thực hiện hành động
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Két sắt bị tác động (nếu hành động này liên quan đến một Vault cụ thể)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id")
    private Vault vault;

    // Loại hành động
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    // Mô tả chi tiết (VD: "Người dùng đã tải lên Di chúc dichuc_2026.pdf")
    @Column(columnDefinition = "TEXT")
    private String description;

    // IP của người dùng (Để chống chối cãi)
    @Column(name = "ip_address")
    private String ipAddress;

    // Thời gian xảy ra hành động (Tự động lưu)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
