package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.DmsConfirmer;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

