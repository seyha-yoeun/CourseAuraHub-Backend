package com.course_aura_hub.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A write request made by course or platform staff. Rows are append-only. */
@Entity
@Table(name = "admin_audit_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_email", nullable = false, updatable = false, columnDefinition = "text")
    private String actorEmail;

    @Column(name = "actor_role", nullable = false, updatable = false, columnDefinition = "text")
    private String actorRole;

    /** The handler, such as {@code CouponController.update}. */
    @Column(name = "action", nullable = false, updatable = false, columnDefinition = "text")
    private String action;

    @Column(name = "http_method", nullable = false, updatable = false, columnDefinition = "text")
    private String httpMethod;

    @Column(name = "path_pattern", nullable = false, updatable = false, columnDefinition = "text")
    private String pathPattern;

    /** What kind of thing was changed, such as {@code coupons} or {@code course-reviews}. */
    @Column(name = "resource", nullable = false, updatable = false, columnDefinition = "text")
    private String resource;

    @Column(name = "resource_id", updatable = false, columnDefinition = "text")
    private String resourceId;

    /** Path variables as a JSON object. */
    @Column(name = "path_variables", updatable = false, columnDefinition = "text")
    private String pathVariables;

    @Column(name = "status", nullable = false, updatable = false)
    private Integer status;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, columnDefinition = "text")
    private AuditOutcome outcome;

    @Column(name = "request_id", updatable = false, columnDefinition = "text")
    private String requestId;

    @Column(name = "ip_address", updatable = false, columnDefinition = "text")
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
