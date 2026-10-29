package com.course_aura_hub.common.email;

/**
 * Email could not be handed to the provider. The message is internal; API responses use a generic text.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
