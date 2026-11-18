package com.course_aura_hub.subscription.service;

import com.course_aura_hub.order.payment.PaymentEvent;
import com.course_aura_hub.order.payment.PaymentSession;
import com.course_aura_hub.subscription.dto.SubscriptionDtos;

import java.util.List;

/**
 * The transactional steps of subscriptions. Provider calls happen in {@code SubscriptionFacade}, between these
 * steps, so a slow provider never holds row locks.
 */
public interface SubscriptionService {

    List<SubscriptionDtos.Plan> activePlans();

    List<SubscriptionDtos.Plan> allPlans();

    SubscriptionDtos.Plan createPlan(SubscriptionDtos.PlanRequest request);

    /** The code can't change; a new price applies from each subscriber's next renewal. */
    SubscriptionDtos.Plan updatePlan(Integer id, SubscriptionDtos.PlanRequest request);

    /** Opens a pending subscription and its first payment. */
    SubscriptionDtos.OpenPayment open(String email, String planCode, String provider);

    /** Charges the learner's member card for a pending payment and settles it. */
    void payWithCard(String paymentNumber);

    void attachSession(String paymentNumber, PaymentSession session);

    /** A payment that could not start or was declined. */
    void markFailed(String paymentNumber);

    void applyPaymentEvent(PaymentEvent event);

    SubscriptionDtos.MySubscription mine(String email);

    SubscriptionDtos.MySubscription cancel(String email);

    SubscriptionDtos.MySubscription resume(String email);

    /** Enrolls a subscriber in a course for as long as the subscription lasts. */
    void enroll(String email, String courseSlug);

    SubscriptionDtos.AdminPage findForAdmin(String status, int page, int size);

    /** Ends what is over: unpaid past the grace period, cancelled at the period end, abandoned first payments. */
    int expireDue();

    /** Active subscriptions whose period has ended and should renew. */
    List<Integer> findDueRenewals();

    /**
     * Opens the next period's payment and marks the subscription past due until it is paid.
     *
     * @return the payment to start, or null when there is nothing to renew
     */
    SubscriptionDtos.OpenPayment openRenewal(Integer subscriptionId);

    /** The pending payment of my live subscription, or null when nothing is owed. */
    SubscriptionDtos.OpenPayment findOpenPayment(String email);

    /** Tells the learner a renewal needs paying, by link or by topping up their member card. */
    void notifyPaymentNeeded(String paymentNumber);
}
