package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.model.AuditEventType;

import java.time.Duration;
import java.util.UUID;

/**
 * Persistent audit trail of authentication events. Recording never fails the calling flow.
 */
public interface AuthAuditService {

    void record(AuthAuditEntry entry);

    long countRecent(UUID userId, AuditEventType type, Duration window);
}
