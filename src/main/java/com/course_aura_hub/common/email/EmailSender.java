package com.course_aura_hub.common.email;

/**
 * Delivery channel for transactional email. Implementations must never log message bodies,
 * which can contain one-time codes and reset links.
 */
public interface EmailSender {

    /**
     * Delivers the message synchronously.
     *
     * @throws EmailDeliveryException when the provider rejects or cannot accept the message
     */
    void send(EmailMessage message);
}
