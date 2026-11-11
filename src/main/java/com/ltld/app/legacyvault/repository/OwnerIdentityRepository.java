package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.OwnerIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OwnerIdentityRepository extends JpaRepository<OwnerIdentity, UUID> {

    Optional<OwnerIdentity> findByUserId(UUID userId);

    Optional<OwnerIdentity> findByCitizenIdHash(String citizenIdHash);

    @Modifying
    @Query("DELETE FROM OwnerIdentity i WHERE i.user.id = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
