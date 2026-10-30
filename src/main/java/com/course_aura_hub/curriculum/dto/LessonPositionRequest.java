package com.course_aura_hub.curriculum.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LessonPositionRequest(
        @NotNull(message = "Position is required")
        @Min(value = 0, message = "Position cannot be negative")
        @Max(value = 86400, message = "Position must not exceed 24 hours")
        Integer positionSeconds
) {
}
