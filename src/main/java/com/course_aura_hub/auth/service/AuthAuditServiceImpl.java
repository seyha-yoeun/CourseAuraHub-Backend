package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.AuthAuditEvent;
import com.course_aura_hub.auth.repository.AuthAuditEventRepository;
import com.course_aura_hub.auth.security.ClientInfo;
import com.course_aura_hub.auth.security.ClientInfoResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes audit events on a dedicated executor. Writing inline would either be lost on rollback
 * (failed logins) or need a second pooled connection per request (REQUIRES_NEW), which can exhaust
 * the pool under load. Request metadata is captured on the calling thread before hand-off.
 */
@Slf4j
@Service
public class AuthAuditServiceImpl implements AuthAuditService {

    private static final int MAX_EMAIL_LENGTH = 150;
    private static final int MAX_REASON_LENGTH = 100;

    private final AuthAuditEventRepository repository;
    private final ClientInfoResolver clientInfoResolver;
    private final TaskExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthAuditServiceImpl(
            AuthAuditEventRepository repository,
            ClientInfoResolver clientInfoResolver,
            @Qualifier("authAuditExecutor") TaskExecutor executor
    ) {
        this.repository = repository;
        this.clientInfoResolver = clientInfoResolver;
        this.executor = executor;
    }

    @Override
    public void record(AuthAuditEntry entry) {
        try {
            ClientInfo client = clientInfoResolver.current();
            AuthAuditEvent event = AuthAuditEvent.builder()
                    .eventType(entry.type())
                    .outcome(entry.outcome())
                    .userId(entry.userId())
                    .email(truncate(entry.email(), MAX_EMAIL_LENGTH))
                    .sessionId(entry.sessionId())
                    .reason(truncate(entry.reason(), MAX_REASON_LENGTH))
                    .metadata(serialize(entry))
                    .ipAddress(client.ipAddress())
                    .userAgent(client.userAgent())
                    .createdAt(Instant.now())
                    .build();
            executor.execute(() -> persist(event));
        } catch (TaskRejectedException ex) {
            log.error("Audit queue full; dropped event type={} outcome={}", entry.type(), entry.outcome());
        } catch (RuntimeException ex) {
            log.error("Failed to queue audit event type={}: {}", entry.type(), ex.getClass().getSimpleName());
        }
    }

    @Override
    public long countRecent(UUID userId, AuditEventType type, Duration window) {
        return repository.countByUserIdAndEventTypeAndCreatedAtAfter(userId, type, Instant.now().minus(window));
    }

    private void persist(AuthAuditEvent event) {
        try {
            repository.save(event);
        } catch (RuntimeException ex) {
            log.error("Failed to persist audit event type={} outcome={}: {}",
                    event.getEventType(), event.getOutcome(), ex.getClass().getSimpleName());
        }
    }

    private String serialize(AuthAuditEntry entry) {
        if (entry.metadata() == null || entry.metadata().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(entry.metadata());
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
