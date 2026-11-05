package com.course_aura_hub.audit.repository;

import com.course_aura_hub.audit.model.AdminAuditEvent;
import com.course_aura_hub.audit.model.AuditOutcome;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;

public final class AdminAuditSpecifications {

    private AdminAuditSpecifications() {
    }

    public static Specification<AdminAuditEvent> actor(String email) {
        return (root, query, cb) -> !StringUtils.hasText(email)
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("actorEmail")), email.trim().toLowerCase(Locale.ROOT));
    }

    public static Specification<AdminAuditEvent> equalsField(String field, String value) {
        return (root, query, cb) -> !StringUtils.hasText(value)
                ? cb.conjunction()
                : cb.equal(root.get(field), value.trim());
    }

    public static Specification<AdminAuditEvent> outcome(AuditOutcome outcome) {
        return (root, query, cb) -> outcome == null ? cb.conjunction() : cb.equal(root.get("outcome"), outcome);
    }

    public static Specification<AdminAuditEvent> between(Instant from, Instant to) {
        return (root, query, cb) -> cb.and(
                from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                to == null ? cb.conjunction() : cb.lessThan(root.get("createdAt"), to)
        );
    }
}
