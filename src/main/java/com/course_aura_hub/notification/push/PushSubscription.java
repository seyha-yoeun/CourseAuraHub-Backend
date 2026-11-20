package com.course_aura_hub.notification.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.util.UUID;

/** One browser that agreed to receive push messages for a user. */
@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "endpoint", nullable = false, unique = true, columnDefinition = "text")
    private String endpoint;

    /** The browser's public key, base64url. */
    @Column(name = "p256dh", nullable = false, columnDefinition = "text")
    private String p256dh;

    /** The browser's auth secret, base64url. */
    @Column(name = "auth", nullable = false, columnDefinition = "text")
    private String auth;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
