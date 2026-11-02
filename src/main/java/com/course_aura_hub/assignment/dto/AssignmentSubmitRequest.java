package com.course_aura_hub.assignment.dto;

import jakarta.validation.constraints.Size;

public record AssignmentSubmitRequest(

        // The storage:// url from a finished ASSIGNMENT_SUBMISSION upload
        @Size(max = 500, message = "File link is too long")
        String fileUrl,

        @Size(max = 255, message = "File name is too long")
        String fileName,

        @Size(max = 4000, message = "Keep your comment under 4000 characters")
        String comment
) {
}
