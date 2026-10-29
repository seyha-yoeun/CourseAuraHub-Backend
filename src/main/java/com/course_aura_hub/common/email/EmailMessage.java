package com.course_aura_hub.common.email;

/**
 * Provider-agnostic email. {@code htmlBody} is optional; {@code textBody} is always sent.
 */
public record EmailMessage(
        String to,
        String subject,
        String textBody,
        String htmlBody
) {
}
