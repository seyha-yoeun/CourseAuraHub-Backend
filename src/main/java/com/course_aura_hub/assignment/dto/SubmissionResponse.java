package com.course_aura_hub.assignment.dto;

import java.time.Instant;

public record SubmissionResponse(
        Integer id,
        // A short-lived signed link
        String fileUrl,
        String fileName,
        String comment,
        String status,
        Integer score,
        boolean passed,
        String feedback,
        Instant submittedAt,
        Instant gradedAt
) {
}
