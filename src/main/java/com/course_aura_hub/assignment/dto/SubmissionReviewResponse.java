package com.course_aura_hub.assignment.dto;

/** A submission as staff see it. No contact details, because instructors aren't admins. */
public record SubmissionReviewResponse(
        String studentName,
        Integer lessonId,
        String lessonTitle,
        String courseSlug,
        String courseTitle,
        Integer passScore,
        SubmissionResponse submission
) {
}
