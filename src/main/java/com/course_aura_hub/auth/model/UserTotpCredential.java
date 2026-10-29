package com.course_aura_hub.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Encrypted TOTP shared secret. Unconfirmed until the user proves possession with a valid code.
 */
@Entity
@Table(name = "user_totp_credentials")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTotpCredential {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "secret_ciphertext", nullable = false, length = 255)
    private String secretCiphertext;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    // Highest accepted time step; codes at or below it are rejected to stop replay
    @Column(name = "last_used_step")
    private Long lastUsedStep;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isConfirmed() {
        return confirmedAt != null;
    }
}
