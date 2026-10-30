package com.course_aura_hub.curriculum.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record SectionUpdateRequest(

        @Size(max = 200, message = "Section title must not exceed 200 characters")
        String title,

        @Min(value = 0, message = "Position cannot be negative")
        Integer position
) {
}
