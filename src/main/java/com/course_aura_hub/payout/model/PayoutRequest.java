package com.course_aura_hub.payout.model;

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
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An instructor asking to be paid every earning available at the time. Moves no money by itself. */
@Entity
@Table(name = "payout_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "instructor_id", nullable = false, updatable = false)
    private UUID instructorId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, columnDefinition = "text")
    private String currency;

    /** How the instructor wants to be paid, such as "bank" or "aba". */
    @Column(name = "method", nullable = false, updatable = false, columnDefinition = "text")
    private String method;

    @Column(name = "account_details", nullable = false, updatable = false, columnDefinition = "text")
    private String accountDetails;

    @Column(name = "note", updatable = false, columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private PayoutStatus status;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    /** The bank or wallet transfer reference recorded when it was paid. */
    @Column(name = "payment_reference", columnDefinition = "text")
    private String paymentReference;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
