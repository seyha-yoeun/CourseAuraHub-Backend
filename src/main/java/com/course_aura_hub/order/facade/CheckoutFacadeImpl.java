package com.course_aura_hub.order.facade;

import com.course_aura_hub.cart.dto.CheckoutResponse;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.course_aura_hub.membercard.service.MemberCardService;
import com.course_aura_hub.order.config.PaymentProperties;
import com.course_aura_hub.order.dto.OrderResponse;
import com.course_aura_hub.order.model.Order;
import com.course_aura_hub.order.model.OrderGroup;
import com.course_aura_hub.order.payment.PaymentGateway;
import com.course_aura_hub.order.payment.PaymentMethod;
import com.course_aura_hub.order.payment.PaymentSession;
import com.course_aura_hub.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Keeps provider calls outside database transactions, so a slow provider never holds row locks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutFacadeImpl implements CheckoutFacade {

    private final OrderService orderService;
    private final ObjectProvider<PaymentGateway> paymentGateway;
    private final PaymentProperties paymentProperties;
    private final MemberCardService memberCardService;

    @Override
    public OrderResponse checkout(String email, String courseSlug, String couponCode, String paymentMethod) {
        boolean byCard = PaymentMethod.fromValue(paymentMethod) == PaymentMethod.MEMBER_CARD;
        if (byCard) {
            memberCardService.requireUsableCard(email);
        }
        PaymentGateway gateway = paymentGateway.getIfAvailable();
        Order order = orderService.openOrder(
                email,
                courseSlug,
                couponCode,
                paymentProperties.currency(),
                provider(byCard, gateway)
        );
        return pay(email, order, byCard, gateway);
    }

    @Override
    public OrderResponse checkoutGift(String email, String courseSlug, String paymentMethod, String recipientEmail, String message) {
        boolean byCard = PaymentMethod.fromValue(paymentMethod) == PaymentMethod.MEMBER_CARD;
        if (byCard) {
            memberCardService.requireUsableCard(email);
        }
        PaymentGateway gateway = paymentGateway.getIfAvailable();
        Order order = orderService.openGiftOrder(email, courseSlug, paymentProperties.currency(), provider(byCard, gateway),
                recipientEmail, message);
        return pay(email, order, byCard, gateway);
    }

    /** Settles a free order, charges a member card, or starts a provider payment, outside any transaction. */
    private OrderResponse pay(String email, Order order, boolean byCard, PaymentGateway gateway) {
        if (order.getAmount().signum() == 0) {
            return orderService.completeFreeOrder(order.getOrderNumber());
        }
        if (byCard) {
            try {
                return orderService.payWithMemberCard(email, order.getOrderNumber());
            } catch (BusinessRuleException ex) {
                orderService.markFailed(order.getOrderNumber());
                throw ex;
            }
        }
        if (order.getCheckoutUrl() != null) {
            return orderService.findMine(email, order.getOrderNumber());
        }

        PaymentSession session;
        try {
            session = gateway.createPayment(order);
        } catch (RuntimeException ex) {
            log.error("Payment provider failed for order={}", order.getOrderNumber(), ex);
            orderService.markFailed(order.getOrderNumber());
            throw new ServiceUnavailableException("Payment could not be started. Please try again.", ex);
        }
        return orderService.attachSession(order.getOrderNumber(), session);
    }

    @Override
    public CheckoutResponse checkoutCart(String email, String couponCode, String paymentMethod) {
        boolean byCard = PaymentMethod.fromValue(paymentMethod) == PaymentMethod.MEMBER_CARD;
        if (byCard) {
            memberCardService.requireUsableCard(email);
        }
        PaymentGateway gateway = paymentGateway.getIfAvailable();
        OrderGroup group = orderService.openGroup(
                email,
                couponCode,
                paymentProperties.currency(),
                provider(byCard, gateway)
        );
        if (group.getAmount().signum() == 0) {
            return orderService.completeFreeGroup(group.getGroupNumber());
        }
        if (byCard) {
            try {
                return orderService.payGroupWithMemberCard(email, group.getGroupNumber());
            } catch (BusinessRuleException ex) {
                orderService.markGroupFailed(group.getGroupNumber());
                throw ex;
            }
        }

        PaymentSession session;
        try {
            session = gateway.createGroupPayment(group.getGroupNumber(), group.getAmount(), group.getCurrency());
        } catch (RuntimeException ex) {
            log.error("Payment provider failed for group={}", group.getGroupNumber(), ex);
            orderService.markGroupFailed(group.getGroupNumber());
            throw new ServiceUnavailableException("Payment could not be started. Please try again.", ex);
        }
        return orderService.attachGroupSession(group.getGroupNumber(), session);
    }

    @Override
    public void handleWebhook(String provider, String signature, String payload) {
        PaymentGateway gateway = paymentGateway.getIfAvailable();
        if (gateway == null || !gateway.name().equals(provider)) {
            throw ResourceNotFoundException.of("Payment provider", provider);
        }
        orderService.applyPaymentEvent(gateway.parseWebhook(signature, payload));
    }

    @Override
    public OrderResponse refund(String orderNumber) {
        // Card payments are refunded to the card, so they work with no provider configured
        return orderService.refund(orderNumber, paymentGateway.getIfAvailable());
    }

    private String provider(boolean byCard, PaymentGateway gateway) {
        if (byCard) {
            return PaymentMethod.MEMBER_CARD_PROVIDER;
        }
        return gateway == null ? null : gateway.name();
    }
}
