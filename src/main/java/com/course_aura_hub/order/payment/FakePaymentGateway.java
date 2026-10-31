package com.course_aura_hub.order.payment;

import com.course_aura_hub.order.config.PaymentProperties;
import com.course_aura_hub.order.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Development stand-in for a real provider. It moves no money: whoever holds the webhook
 * secret can mark an order paid, so never enable it in production.
 *
 * <p>Webhook payload: {@code ORDER_NUMBER:PAID|FAILED:AMOUNT:CURRENCY}, signed with hex HMAC-SHA256.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.payment", name = "provider", havingValue = FakePaymentGateway.NAME)
public class FakePaymentGateway implements PaymentGateway {

    public static final String NAME = "fake";

    private static final String HMAC = "HmacSHA256";

    private final byte[] webhookSecret;

    public FakePaymentGateway(PaymentProperties properties) {
        if (!StringUtils.hasText(properties.webhookSecret())) {
            throw new IllegalStateException("app.payment.webhook-secret is required for the fake payment provider");
        }
        this.webhookSecret = properties.webhookSecret().getBytes(StandardCharsets.UTF_8);
        log.warn("Fake payment provider is active: no real money moves. Do not use in production.");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PaymentSession createPayment(Order order) {
        String reference = "fake_" + UUID.randomUUID();
        return new PaymentSession(reference, "https://payments.invalid/checkout/" + reference);
    }

    @Override
    public PaymentSession createGroupPayment(String groupNumber, BigDecimal amount, String currency) {
        String reference = "fake_" + UUID.randomUUID();
        return new PaymentSession(reference, "https://payments.invalid/checkout/" + reference);
    }

    @Override
    public PaymentEvent parseWebhook(String signature, String payload) {
        byte[] expected = sign(payload).getBytes(StandardCharsets.UTF_8);
        byte[] actual = (signature == null ? "" : signature).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidWebhookException("Invalid webhook signature");
        }

        String[] parts = payload.split(":");
        if (parts.length != 4) {
            throw new InvalidWebhookException("Invalid webhook payload");
        }
        try {
            return new PaymentEvent(
                    parts[0],
                    PaymentEvent.Outcome.valueOf(parts[1]),
                    new BigDecimal(parts[2]),
                    parts[3]
            );
        } catch (IllegalArgumentException ex) {
            throw new InvalidWebhookException("Invalid webhook payload");
        }
    }

    @Override
    public void refund(Order order) {
        log.info("Fake refund for order={}", order.getOrderNumber());
    }

    /** Hex HMAC of a payload, as a provider would sign it. */
    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(webhookSecret, HMAC));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC is not available", ex);
        }
    }
}
