package com.course_aura_hub.order.service;

/** A gift order was opened, inside its transaction, with who it is for. */
public record GiftOrderOpened(Integer orderId, String recipientEmail, String message) {
}
