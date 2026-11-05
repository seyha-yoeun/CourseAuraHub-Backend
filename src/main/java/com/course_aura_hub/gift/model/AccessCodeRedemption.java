package com.course_aura_hub.gift.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "access_code_redemptions")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessCodeRedemption {

    @EmbeddedId
    private Id id;

    @Column(name = "redeemed_at", nullable = false, updatable = false)
    private Instant redeemedAt;

    @PrePersist
    void onCreate() {
        if (redeemedAt == null) {
            redeemedAt = Instant.now();
        }
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "code_id", nullable = false)
        private Integer codeId;

        @Column(name = "student_id", nullable = false)
        private Integer studentId;
    }
}
