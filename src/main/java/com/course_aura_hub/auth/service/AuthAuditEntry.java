package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.AuditOutcome;
import com.course_aura_hub.auth.model.User;
import lombok.Builder;

import java.util.Map;
import java.util.UUID;

/**
 * A security event to record. {@code metadata} must only hold non-sensitive values
 * (never passwords, codes or tokens).
 */
@Builder
public record AuthAuditEntry(
        AuditEventType type,
        AuditOutcome outcome,
        UUID userId,
        String email,
        UUID sessionId,
        String reason,
        Map<String, String> metadata
) {

    public static AuthAuditEntryBuilder success(AuditEventType type, User user) {
        return forUser(type, AuditOutcome.SUCCESS, user);
    }

    public static AuthAuditEntryBuilder failure(AuditEventType type, User user, String reason) {
        return forUser(type, AuditOutcome.FAILURE, user).reason(reason);
    }

    private static AuthAuditEntryBuilder forUser(AuditEventType type, AuditOutcome outcome, User user) {
        AuthAuditEntryBuilder builder = AuthAuditEntry.builder().type(type).outcome(outcome);
        if (user != null) {
            builder.userId(user.getId()).email(user.getEmail());
        }
        return builder;
    }
}
