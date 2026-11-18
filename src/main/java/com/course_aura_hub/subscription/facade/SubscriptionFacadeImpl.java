package com.course_aura_hub.subscription.facade;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.course_aura_hub.membercard.service.MemberCardService;
import com.course_aura_hub.order.payment.PaymentGateway;
import com.course_aura_hub.order.payment.PaymentMethod;
import com.course_aura_hub.order.payment.PaymentSession;
import com.course_aura_hub.subscription.dto.SubscriptionDtos;
import com.course_aura_hub.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionFacadeImpl implements SubscriptionFacade {

    private final SubscriptionService subscriptionService;
    private final ObjectProvider<PaymentGateway> paymentGateway;
    private final MemberCardService memberCardService;

    @Override
    public SubscriptionDtos.MySubscription subscribe(String email, String planCode, String paymentMethod) {
        boolean byCard = PaymentMethod.fromValue(paymentMethod) == PaymentMethod.MEMBER_CARD;
        if (byCard) {
            memberCardService.requireUsableCard(email);
        }
        PaymentGateway gateway = paymentGateway.getIfAvailable();
        if (!byCard && gateway == null) {
            throw new ServiceUnavailableException("Paid checkout is not available yet");
        }
        SubscriptionDtos.OpenPayment payment = subscriptionService.open(email, planCode,
                byCard ? PaymentMethod.MEMBER_CARD_PROVIDER : gateway.name());
        try {
            pay(payment, gateway);
        } catch (BusinessRuleException | ServiceUnavailableException ex) {
            // A sign-up whose first payment can't start is cancelled, so the learner can simply try again
            subscriptionService.markFailed(payment.paymentNumber());
            throw ex;
        }
        return subscriptionService.mine(email);
    }

    @Override
    public SubscriptionDtos.MySubscription payOpen(String email) {
        SubscriptionDtos.OpenPayment payment = subscriptionService.findOpenPayment(email);
        if (payment == null) {
            throw new BusinessRuleException("Your subscription has nothing to pay right now");
        }
        pay(payment, paymentGateway.getIfAvailable());
        return subscriptionService.mine(email);
    }

    @Override
    public int renewDue() {
        int opened = 0;
        PaymentGateway gateway = paymentGateway.getIfAvailable();
        for (Integer id : subscriptionService.findDueRenewals()) {
            try {
                SubscriptionDtos.OpenPayment payment = subscriptionService.openRenewal(id);
                if (payment == null) {
                    continue;
                }
                opened++;
                if (PaymentMethod.MEMBER_CARD_PROVIDER.equals(payment.provider())) {
                    try {
                        subscriptionService.payWithCard(payment.paymentNumber());
                    } catch (BusinessRuleException ex) {
                        // The renewal stays open; the learner can top up and pay within the grace period
                        subscriptionService.notifyPaymentNeeded(payment.paymentNumber());
                    }
                } else if (gateway != null && gateway.name().equals(payment.provider())) {
                    startProviderPayment(payment, gateway);
                    subscriptionService.notifyPaymentNeeded(payment.paymentNumber());
                } else {
                    subscriptionService.notifyPaymentNeeded(payment.paymentNumber());
                }
            } catch (RuntimeException ex) {
                log.warn("Subscription renewal failed id={}: {}", id, ex.getMessage());
            }
        }
        return opened;
    }

    private void pay(SubscriptionDtos.OpenPayment payment, PaymentGateway gateway) {
        if (PaymentMethod.MEMBER_CARD_PROVIDER.equals(payment.provider())) {
            subscriptionService.payWithCard(payment.paymentNumber());
            return;
        }
        if (gateway == null || !gateway.name().equals(payment.provider())) {
            throw new ServiceUnavailableException("Paid checkout is not available yet");
        }
        startProviderPayment(payment, gateway);
    }

    private void startProviderPayment(SubscriptionDtos.OpenPayment payment, PaymentGateway gateway) {
        PaymentSession session;
        try {
            // The provider echoes the SUB- number in its webhook, which routes it back to subscriptions
            session = gateway.createGroupPayment(payment.paymentNumber(), payment.amount(), payment.currency());
        } catch (RuntimeException ex) {
            log.error("Payment provider failed for subscription payment={}", payment.paymentNumber(), ex);
            throw new ServiceUnavailableException("Payment could not be started. Please try again.", ex);
        }
        subscriptionService.attachSession(payment.paymentNumber(), session);
    }
}
