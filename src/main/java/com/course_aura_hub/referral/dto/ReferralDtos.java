package com.course_aura_hub.referral.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Referral shapes. People a learner referred appear by first name only. */
public final class ReferralDtos {

    private ReferralDtos() {
    }

    public record ClaimRequest(@NotBlank(message = "Enter a referral code") @Size(max = 20) String code) {
    }

    public record ClaimResponse(String referrerFirstName) {
    }

    public record ReferredFriend(String firstName, String status, Instant joinedAt, Instant rewardedAt) {
    }

    /**
     * @param earned total credited to my member card for referrals
     */
    public record MyReferral(String code, String link, BigDecimal rewardAmount, String currency, long signedUp,
                             long qualified, long rewarded, BigDecimal earned, List<ReferredFriend> friends) {
    }

    public record AdminReferral(Integer id, String referrerName, String referrerEmail, String refereeName,
                                String refereeEmail, String status, String orderNumber, BigDecimal rewardAmount,
                                Instant createdAt, Instant qualifiedAt, Instant rewardedAt) {
    }

    public record AdminPage(List<AdminReferral> items, int page, int size, long totalElements, int totalPages) {
    }
}
