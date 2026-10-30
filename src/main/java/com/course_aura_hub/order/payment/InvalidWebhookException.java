package com.course_aura_hub.order.payment;

import com.course_aura_hub.common.exception.ForbiddenActionException;

public class InvalidWebhookException extends ForbiddenActionException {

    public InvalidWebhookException(String message) {
        super(message);
    }
}
