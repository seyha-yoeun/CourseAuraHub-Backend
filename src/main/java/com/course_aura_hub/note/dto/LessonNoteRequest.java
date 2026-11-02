package com.course_aura_hub.note.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record LessonNoteRequest(
        @Size(max = 4000, message = "Keep notes under 4000 characters")
        String body,

        @Min(value = 0, message = "A video position can't be negative")
        Integer positionSeconds
) {
}
