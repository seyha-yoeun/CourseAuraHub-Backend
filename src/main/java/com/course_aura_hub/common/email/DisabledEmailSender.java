package com.course_aura_hub.common.email;

import lombok.extern.slf4j.Slf4j;

/**
 * Active when 'app.mail.enabled=false'. Fails loudly instead of pretending mail went out, so
 * flows that depend on email (verification, reset) never report false success.
 */
@Slf4j
public class DisabledEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage message) {
        log.warn("Email delivery is disabled (app.mail.enabled=false); dropped subject=\"{}\"", message.subject());
        throw new EmailDeliveryException("Email delivery is disabled");
    }
}
