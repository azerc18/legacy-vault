package com.ltld.app.legacyvault.beneficiary.repository;

import com.ltld.app.legacyvault.beneficiary.entity.IdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, UUID> {
}
