package com.course_aura_hub.membercard.dto;

import java.util.List;

public record MemberCardApplicationPageResponse(
        List<MemberCardApplicationResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
