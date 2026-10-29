package com.course_aura_hub.auth.repository;

import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.AuthAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuthAuditEventRepository extends JpaRepository<AuthAuditEvent, Long> {

    long countByUserIdAndEventTypeAndCreatedAtAfter(UUID userId, AuditEventType eventType, Instant after);

    List<AuthAuditEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AuthAuditEvent> findByEmailOrderByCreatedAtDesc(String email);
}
