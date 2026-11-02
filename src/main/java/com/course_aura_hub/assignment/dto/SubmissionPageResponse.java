package com.course_aura_hub.assignment.dto;

import java.util.List;

public record SubmissionPageResponse(
        List<SubmissionReviewResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
