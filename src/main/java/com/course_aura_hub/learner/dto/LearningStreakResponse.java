package com.course_aura_hub.learner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Consecutive days with at least one completed lesson, in the requested time zone")
public record LearningStreakResponse(

        @Schema(description = "Current streak; still counts when the learner studied yesterday but not yet today", example = "4")
        int currentDays,

        @Schema(description = "Longest streak ever", example = "9")
        int longestDays,

        @Schema(description = "Whether a lesson was completed today", example = "true")
        boolean activeToday
) {
}
