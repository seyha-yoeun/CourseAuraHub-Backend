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

/** An append-only ledger entry. Rows are never changed once written. */
@Entity
@Table(name = "member_card_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberCardTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false, updatable = false)
    private MemberCard card;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, columnDefinition = "text")
    private MemberCardTransactionType type;

    // Always positive; the type says which way the money moved
    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    // The order or order group this paid for or refunded
    @Column(name = "reference", updatable = false, columnDefinition = "text")
    private String reference;

    @Column(name = "idempotency_key", updatable = false, columnDefinition = "text")
    private String idempotencyKey;

    @Column(name = "note", updatable = false, columnDefinition = "text")
    private String note;

    @Column(name = "performed_by", updatable = false)
    private UUID performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
