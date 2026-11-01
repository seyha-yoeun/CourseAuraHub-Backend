package com.course_aura_hub.membercard.dto;

import java.util.List;

public record MemberCardTransactionPageResponse(
        List<MemberCardTransactionResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
