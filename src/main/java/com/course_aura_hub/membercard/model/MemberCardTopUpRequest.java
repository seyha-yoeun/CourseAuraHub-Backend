package com.course_aura_hub.membercard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "member_card_top_up_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberCardTopUpRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false, updatable = false)
    private MemberCard card;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "note", updatable = false, columnDefinition = "text")
    private String note;

    // A storage:// reference, signed only when shown
    @Column(name = "receipt_url", updatable = false, columnDefinition = "text")
    private String receiptUrl;

    @Column(name = "receipt_name", updatable = false, columnDefinition = "text")
    private String receiptName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private MemberCardTopUpStatus status;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
