package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.SecurityAlert;
import com.ltld.app.legacyvault.enums.SecurityAlertRule;
import com.ltld.app.legacyvault.enums.SecurityAlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, UUID> {
    Optional<SecurityAlert> findFirstByRuleAndSubjectAndStatus(SecurityAlertRule rule, String subject,
                                                               SecurityAlertStatus status);

    boolean existsByRuleAndSubject(SecurityAlertRule rule, String subject);

    Page<SecurityAlert> findByStatusOrderByCreatedAtDesc(SecurityAlertStatus status, Pageable pageable);

    long countByStatus(SecurityAlertStatus status);
}