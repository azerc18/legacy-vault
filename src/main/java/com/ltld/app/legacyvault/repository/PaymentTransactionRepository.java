package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    @EntityGraph(attributePaths = {"plan"})
    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "plan"})
    @Query("SELECT p FROM PaymentTransaction p WHERE p.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") UUID id);
}

