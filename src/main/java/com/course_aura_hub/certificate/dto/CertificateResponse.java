package com.course_aura_hub.certificate.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A course completion certificate held by the current learner")
public record CertificateResponse(

        @Schema(description = "Public certificate number, used for verification", example = "CAH-7K2M-Q9X4-ZD3P-8HRT")
        String certificateNumber,

        @Schema(description = "Completed course slug", example = "git-github-fundamentals")
        String courseSlug,

        @Schema(description = "Completed course title")
        String courseTitle,

        @Schema(description = "Course instructor")
        String instructor,

        @Schema(description = "Course thumbnail URL")
        String thumbnail,

        @Schema(description = "When the certificate was issued")
        Instant issuedAt
) {
}
