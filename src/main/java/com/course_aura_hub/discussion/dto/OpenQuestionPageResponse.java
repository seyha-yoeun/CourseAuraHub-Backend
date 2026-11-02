package com.course_aura_hub.discussion.dto;

import java.util.List;

public record OpenQuestionPageResponse(
        List<OpenQuestionResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
