package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.PhoneOtpChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PhoneOtpChallengeRepository extends JpaRepository<PhoneOtpChallenge, UUID> {

    // Khoá dòng để hai lần nhập mã song song không cùng vượt qua giới hạn số lần thử
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PhoneOtpChallenge c WHERE c.userId = :userId")
    Optional<PhoneOtpChallenge> findByUserIdForUpdate(@Param("userId") UUID userId);
}
