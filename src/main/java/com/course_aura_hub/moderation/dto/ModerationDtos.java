package com.course_aura_hub.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ModerationDtos {

    private ModerationDtos() {
    }

    /** @param targetType review, question or answer */
    public record ReportRequest(
            @NotBlank String targetType,
            @NotNull Integer targetId,
            @NotBlank @Size(max = 500, message = "Reason must not exceed 500 characters") String reason
    ) {
    }

    /** A report with the reported text, so an admin can decide without opening the course. */
    public record Report(Integer id, String targetType, Integer targetId, String courseSlug, String excerpt, String author,
                         String reason, String status, long reportsOnTarget, Instant createdAt) {
    }

    public record Page(List<Report> items, int page, int size, long totalElements, int totalPages) {
    }
}
