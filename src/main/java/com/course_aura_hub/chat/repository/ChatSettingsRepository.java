package com.course_aura_hub.chat.repository;

import com.course_aura_hub.chat.model.ChatSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatSettingsRepository extends JpaRepository<ChatSettings, UUID> {
}
