package com.course_aura_hub.order.service;

/** A paid order was refunded, inside the refunding transaction. */
public record OrderRefunded(Integer orderId) {
}
