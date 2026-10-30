package com.course_aura_hub.order.dto;

import java.util.List;

public record AdminOrderPageResponse(
        List<AdminOrderResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
