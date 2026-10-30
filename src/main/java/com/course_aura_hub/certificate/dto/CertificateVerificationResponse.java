package com.course_aura_hub.certificate.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Public view of a certificate for third-party verification; carries no contact details")
public record CertificateVerificationResponse(

        @Schema(description = "Certificate number", example = "CAH-7K2M-Q9X4-ZD3P-8HRT")
        String certificateNumber,

        @Schema(description = "Name of the learner the certificate was issued to")
        String learnerName,

        @Schema(description = "Completed course title")
        String courseTitle,

        @Schema(description = "Course instructor")
        String instructor,

        @Schema(description = "When the certificate was issued")
        Instant issuedAt
) {
}
