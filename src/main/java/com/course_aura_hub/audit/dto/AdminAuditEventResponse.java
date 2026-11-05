package com.course_aura_hub.audit.dto;

import com.course_aura_hub.audit.model.AdminAuditEvent;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditEventResponse(
        Long id,
        UUID actorId,
        String actorEmail,
        String actorRole,
        String action,
        String httpMethod,
        String pathPattern,
        String resource,
        String resourceId,
        String pathVariables,
        int status,
        String outcome,
        String requestId,
        String ipAddress,
        Instant createdAt
) {

    public static AdminAuditEventResponse from(AdminAuditEvent event) {
        return new AdminAuditEventResponse(
                event.getId(),
                event.getActorId(),
                event.getActorEmail(),
                event.getActorRole(),
                event.getAction(),
                event.getHttpMethod(),
                event.getPathPattern(),
                event.getResource(),
                event.getResourceId(),
                event.getPathVariables(),
                event.getStatus(),
                event.getOutcome().name(),
                event.getRequestId(),
                event.getIpAddress(),
                event.getCreatedAt()
        );
    }
}
