package com.course_aura_hub.settings.repository;

import com.course_aura_hub.settings.model.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Short> {
}
