package com.course_aura_hub.chat.repository;

import com.course_aura_hub.chat.model.ChatRestriction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ChatRestrictionRepository extends JpaRepository<ChatRestriction, UUID> {

    Optional<ChatRestriction> findByUserIdAndUntilAfter(UUID userId, Instant now);
}
