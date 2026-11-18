package com.course_aura_hub.subscription.model;

/** PENDING until the first payment; PAST_DUE while a renewal is unpaid inside the grace period; CANCELLED if never paid; EXPIRED once it has ended. */
public enum SubscriptionStatus {
    PENDING,
    ACTIVE,
    PAST_DUE,
    CANCELLED,
    EXPIRED
}
