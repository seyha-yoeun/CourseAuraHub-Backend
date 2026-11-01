package com.course_aura_hub.membercard.dto;

import java.util.List;

public record MemberCardPageResponse(
        List<MemberCardResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
