package com.course_aura_hub.payout.model;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.order.model.Order;
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

/** The instructor's share of one paid order (SALE), or the share taken back after a late refund (REFUND). */
@Entity
@Table(name = "instructor_earnings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstructorEarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "instructor_id", nullable = false, updatable = false)
    private UUID instructorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, updatable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, columnDefinition = "text")
    private EarningType type;

    @Column(name = "gross", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal gross;

    @Column(name = "platform_fee", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal platformFee;

    /** What the instructor receives; negative on a REFUND row. */
    @Column(name = "net", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal net;

    @Column(name = "share_percent", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal sharePercent;

    @Column(name = "currency", nullable = false, updatable = false, columnDefinition = "text")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private EarningStatus status;

    @Column(name = "available_at", nullable = false, updatable = false)
    private Instant availableAt;

    /** Set while a payout request has claimed this row. */
    @Column(name = "payout_id")
    private Integer payoutId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
