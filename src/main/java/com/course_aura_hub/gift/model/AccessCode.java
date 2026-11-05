package com.course_aura_hub.gift.model;

import com.course_aura_hub.course.model.Course;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A code that gives free access to one course. Only its hash and last characters are stored. */
@Entity
@Table(name = "access_codes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "code_hash", nullable = false, unique = true, columnDefinition = "text")
    private String codeHash;

    /** The last four characters, so people can tell codes apart. */
    @Column(name = "code_hint", nullable = false, columnDefinition = "text")
    private String codeHint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, updatable = false)
    private Course course;

    @Column(name = "batch", updatable = false, columnDefinition = "text")
    private String batch;

    @Column(name = "max_uses", nullable = false)
    private Integer maxUses;

    @Column(name = "uses", nullable = false)
    private Integer uses;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "gift_order_id", updatable = false)
    private Integer giftOrderId;

    @Column(name = "purchaser_student_id", updatable = false)
    private Integer purchaserStudentId;

    @Column(name = "recipient_email", updatable = false, columnDefinition = "text")
    private String recipientEmail;

    @Column(name = "message", updatable = false, columnDefinition = "text")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
