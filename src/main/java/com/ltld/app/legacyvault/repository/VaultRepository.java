package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.Vault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VaultRepository extends JpaRepository<Vault, UUID> {
    // JPA tự động viết câu query SQL để tìm tất cả Vault thuộc về 1 người dùng (Owner)
    List<Vault> findByOwnerId(UUID ownerId);
}
