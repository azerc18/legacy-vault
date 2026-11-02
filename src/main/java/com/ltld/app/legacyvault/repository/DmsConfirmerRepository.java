package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.DmsConfirmer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DmsConfirmerRepository extends JpaRepository<DmsConfirmer, UUID> {

    List<DmsConfirmer> findAllByDmsConfigIdOrderByCreatedAtAsc(UUID dmsConfigId);

    long countByDmsConfigId(UUID dmsConfigId);

    boolean existsByDmsConfigIdAndEmailIgnoreCase(UUID dmsConfigId, String email);

    boolean existsByLastTokenJti(String lastTokenJti);

    Optional<DmsConfirmer> findByIdAndDmsConfigId(UUID id, UUID dmsConfigId);

    // X2 (m1): email này có đang là người xác nhận chéo của Owner không (email truyền vào đã lowercase)
    @Query("select count(c) > 0 from DmsConfirmer c where c.dmsConfig.owner.id = :ownerId and lower(c.email) = :email")
    boolean existsByOwnerIdAndEmail(@Param("ownerId") UUID ownerId, @Param("email") String email);
}

