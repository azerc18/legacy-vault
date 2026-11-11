package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Coupon> findAllByOrderByCreatedAtDesc();

    /** Tăng lượt dùng khi giao dịch thành công; một câu UPDATE nên hai IPN song song không mất lượt. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("UPDATE Coupon c SET c.redeemedCount = c.redeemedCount + 1 WHERE LOWER(c.code) = LOWER(:code)")
    int incrementRedeemed(@Param("code") String code);
}
