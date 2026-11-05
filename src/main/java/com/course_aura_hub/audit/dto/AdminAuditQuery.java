package com.course_aura_hub.audit.dto;

import java.time.Instant;

/** Every filter is optional. {@code to} is exclusive. */
public record AdminAuditQuery(
        String actorEmail,
        String resource,
        String resourceId,
        String action,
        String outcome,
        Instant from,
        Instant to
) {
}
