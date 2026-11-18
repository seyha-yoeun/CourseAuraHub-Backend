package com.course_aura_hub.referral.service;

import com.course_aura_hub.referral.dto.ReferralDtos;

import java.util.UUID;

/** Referral codes, claims, qualification by a first paid order, and the delayed member card reward. */
public interface ReferralService {

    /** My code (created on first use), link and the people I referred. */
    ReferralDtos.MyReferral mine(UUID userId);

    /** A new user enters someone's code, within the claim window and before their first paid order. */
    ReferralDtos.ClaimResponse claim(UUID userId, String code);

    /** Credits referrers whose referral has been qualified for longer than the hold. @return how many */
    int rewardDue();

    /** @param status signed_up, qualified, rewarded or blank for all */
    ReferralDtos.AdminPage findForAdmin(String status, int page, int size);
}
