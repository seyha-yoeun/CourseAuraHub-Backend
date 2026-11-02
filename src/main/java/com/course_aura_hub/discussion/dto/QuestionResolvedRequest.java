package com.course_aura_hub.discussion.dto;

import jakarta.validation.constraints.NotNull;

public record QuestionResolvedRequest(
        @NotNull(message = "Say whether the question is resolved")
        Boolean resolved
) {
}
