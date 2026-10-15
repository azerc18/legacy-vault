package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.DmsCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DmsCheckinRepository extends JpaRepository<DmsCheckin, UUID> {
    List<DmsCheckin> findAllByDmsConfigIdOrderByOccurredAtDesc(UUID dmsConfigId);
}
