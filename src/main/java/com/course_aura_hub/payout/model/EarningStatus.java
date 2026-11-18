package com.course_aura_hub.payout.model;

/** PENDING waits out the hold, AVAILABLE can be paid out, PAID was paid, REVERSED was refunded before payout. */
public enum EarningStatus {
    PENDING,
    AVAILABLE,
    PAID,
    REVERSED
}
