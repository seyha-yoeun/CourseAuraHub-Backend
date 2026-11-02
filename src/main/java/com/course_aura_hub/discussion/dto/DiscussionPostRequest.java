package com.course_aura_hub.discussion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DiscussionPostRequest(
        @NotBlank(message = "Write something first")
        @Size(max = 4000, message = "Keep it under 4000 characters")
        String body
) {
}
