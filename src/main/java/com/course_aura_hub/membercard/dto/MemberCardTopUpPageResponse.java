package com.course_aura_hub.membercard.dto;

import java.util.List;

public record MemberCardTopUpPageResponse(
        List<MemberCardTopUpResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
