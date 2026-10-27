package com.course_aura_hub.curriculum.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LessonCreateRequest(

        @NotBlank(message = "Lesson title is required")
        @Size(max = 200, message = "Lesson title must not exceed 200 characters")
        String title,

        String type,

        @Min(value = 0, message = "Duration cannot be negative")
        @Max(value = 1440, message = "Duration must not exceed 1440 minutes")
        Integer durationMinutes,

        @Size(max = 1000, message = "Content URL must not exceed 1000 characters")
        String contentUrl,

        Boolean preview,

        @Min(value = 0, message = "Position cannot be negative")
        Integer position
) {
}
