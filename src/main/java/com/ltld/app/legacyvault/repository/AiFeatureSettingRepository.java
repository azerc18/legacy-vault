package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.AiFeatureSetting;
import com.ltld.app.legacyvault.enums.AiFeature;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiFeatureSettingRepository extends JpaRepository<AiFeatureSetting, AiFeature> {
}
