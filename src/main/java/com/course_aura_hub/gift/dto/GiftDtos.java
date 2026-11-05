package com.course_aura_hub.gift.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class GiftDtos {

    private GiftDtos() {
    }

    /** @param paymentMethod ONLINE (default) or MEMBER_CARD */
    public record GiftRequest(
            @NotBlank String courseSlug,
            @NotBlank @Email(message = "Recipient email must be valid") String recipientEmail,
            @Size(max = 500, message = "Message must not exceed 500 characters") String message,
            String paymentMethod
    ) {
    }

    public record RedeemRequest(@NotBlank @Size(max = 64) String code) {
    }

    public record IssueRequest(
            @NotBlank String courseSlug,
            @Min(1) @Max(500) int quantity,
            @Min(1) @Max(100000) Integer maxUses,
            Instant expiresAt,
            @Size(max = 100) String batch
    ) {
    }

    /** A code as admins and buyers see it after issue: the full code is never shown again. */
    public record Code(Integer id, String hint, String courseSlug, String courseTitle, String batch, int uses, int maxUses,
                       Instant expiresAt, boolean revoked, String status, Instant createdAt) {
    }

    /** Freshly issued codes; this is the only time the full codes are returned. */
    public record IssuedBatch(String batch, String courseSlug, List<String> codes) {
    }

    /** A gift as its buyer sees it. */
    public record SentGift(Integer codeId, String recipientEmail, String courseSlug, String courseTitle, String status, Instant sentAt) {
    }

    public record Redeemed(String courseSlug, String courseTitle) {
    }
}
