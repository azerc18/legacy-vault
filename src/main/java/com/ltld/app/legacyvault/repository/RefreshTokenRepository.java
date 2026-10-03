package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,UUID> {
}
