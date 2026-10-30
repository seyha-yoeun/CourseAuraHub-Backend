package com.course_aura_hub.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param provider      active gateway; blank disables paid checkout
 * @param currency      ISO 4217 code that orders are charged in
 * @param webhookSecret shared secret used to authenticate provider webhooks
 */
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(String provider, String currency, String webhookSecret) {

    public PaymentProperties {
        currency = currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase();
    }
}
