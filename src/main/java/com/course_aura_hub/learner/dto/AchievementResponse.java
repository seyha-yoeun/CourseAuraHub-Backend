package com.course_aura_hub.learner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A badge and the learner's progress toward it")
public record AchievementResponse(

        @Schema(description = "Stable identifier", example = "WEEK_WARRIOR")
        String code,

        @Schema(example = "Week Warrior")
        String title,

        @Schema(example = "Learn 7 days in a row")
        String description,

        @Schema(description = "What is being counted", example = "longest_streak_days")
        String metric,

        @Schema(description = "Current value, capped at the target", example = "4")
        long progress,

        @Schema(example = "7")
        long target,

        boolean earned
) {
}
