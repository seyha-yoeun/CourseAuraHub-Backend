package com.course_aura_hub.subscription.facade;

import com.course_aura_hub.subscription.dto.SubscriptionDtos;

/** Subscription payments, with provider calls kept outside database transactions. */
public interface SubscriptionFacade {

    /** Starts a subscription: charges the member card at once, or returns a checkout link for the provider. */
    SubscriptionDtos.MySubscription subscribe(String email, String planCode, String paymentMethod);

    /** Pays whatever my subscription owes now, such as a renewal that failed on the card. */
    SubscriptionDtos.MySubscription payOpen(String email);

    /** Opens and starts payments for every renewal that is due. @return how many renewals were opened */
    int renewDue();
}
