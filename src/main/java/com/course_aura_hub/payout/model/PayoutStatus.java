package com.course_aura_hub.payout.model;

/** An instructor's request moves through admin approval to being paid, or is rejected. */
public enum PayoutStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    PAID
}
