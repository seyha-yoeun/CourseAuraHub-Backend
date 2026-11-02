package com.course_aura_hub.assignment.dto;

public record AssignmentResponse(
        Integer id,
        Integer lessonId,
        String instructions,
        Integer passScore,
        boolean required,
        // The caller's own submission, or null
        SubmissionResponse submission
) {
}
