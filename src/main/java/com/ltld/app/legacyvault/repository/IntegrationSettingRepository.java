package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.IntegrationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationSettingRepository extends JpaRepository<IntegrationSetting, Byte> {
}
