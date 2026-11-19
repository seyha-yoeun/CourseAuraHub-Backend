package com.course_aura_hub.order.service;

/** Any order was paid, inside the paying transaction. Other modules, such as payouts, react to it. */
public record OrderPaid(Integer orderId) {
}
