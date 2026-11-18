package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.DmsPolicySetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DmsPolicySettingRepository extends JpaRepository<DmsPolicySetting, Byte> {
}
