package com.course_aura_hub.order.dto;

public record AdminOrderResponse(
        OrderResponse order,
        Integer studentId,
        String studentEmail,
        String provider,
        String providerReference
) {
}
