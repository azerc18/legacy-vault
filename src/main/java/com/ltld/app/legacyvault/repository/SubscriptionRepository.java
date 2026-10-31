package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @EntityGraph(attributePaths = {"plan"})
    Optional<Subscription> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"plan"})
    Optional<Subscription> findFirstByUserIdAndStatusOrderByExpiresAtDesc(UUID userId, SubscriptionStatus status);

    @EntityGraph(attributePaths = {"user", "plan"})
    List<Subscription> findByStatusAndExpiresAtBefore(SubscriptionStatus status, LocalDateTime now);

    @EntityGraph(attributePaths = {"user", "plan"})
    List<Subscription> findByStatusAndReminderSentFalseAndExpiresAtBetween(
            SubscriptionStatus status, LocalDateTime from, LocalDateTime to);
}

