package com.course_aura_hub.revision.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class RevisionDtos {

    private RevisionDtos() {
    }

    /** One changed field: what learners see now and what the instructor proposes. */
    public record FieldChange(String field, Object before, Object after) {
    }

    public record RevisionResponse(
            Integer id,
            String courseSlug,
            String courseTitle,
            String status,
            String summary,
            List<FieldChange> changes,
            String reviewNote,
            Instant createdAt,
            Instant updatedAt,
            Instant reviewedAt
    ) {
    }

    /** A public changelog entry: which fields changed and the instructor's note, never prices or reviewer notes. */
    public record ChangelogEntry(Instant date, List<String> fields, String summary) {
    }

    public record DecisionRequest(@Size(max = 2000, message = "Note must not exceed 2000 characters") String note) {
    }
}
