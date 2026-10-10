package com.ltld.app.legacyvault.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "verification_request_vaults",
        uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "vault_id"})
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VerificationRequestVault {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private LegalVerificationRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vault_id", nullable = false)
    private Vault vault;
}