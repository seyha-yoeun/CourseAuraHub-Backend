package com.course_aura_hub.learner.dto;

import java.time.LocalDate;

public record DailyActivityResponse(
        LocalDate date,
        long lessonsCompleted
) {
}
