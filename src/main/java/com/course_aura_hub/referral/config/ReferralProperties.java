package com.course_aura_hub.referral.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * @param enabled         accept new referrals
 * @param rewardAmount    member card credit for the referrer, in the payment currency
 * @param holdDays        days after the qualifying payment before the reward; keep it at least the refund window
 * @param claimWindowDays how long after signing up a new user can still enter a code
 * @param linkBaseUrl     sign-up page that reads ?ref=
 */
@ConfigurationProperties(prefix = "app.referrals")
public record ReferralProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5.00") BigDecimal rewardAmount,
        @DefaultValue("14") int holdDays,
        @DefaultValue("14") int claimWindowDays,
        @DefaultValue("http://localhost:3000/auth/register") String linkBaseUrl
) {
}
