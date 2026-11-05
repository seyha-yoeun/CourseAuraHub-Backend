package com.course_aura_hub.order.service;

/** A gift order was paid, inside the paying transaction; the gift module issues its code. */
public record GiftOrderPaid(Integer orderId) {
}
