package com.course_aura_hub.curriculum.dto;

import java.time.LocalDate;

public record LessonResponse(
        Integer id,
        String title,
        String type,
        Integer durationMinutes,
        boolean preview,
        Integer position,
        boolean locked,
        String contentUrl,
        boolean completed,
        // Where the viewer stopped in this video; null if nothing is saved
        Integer resumePositionSeconds,
        // Days after enrolling before the lesson opens; null when it opens straight away
        Integer releaseAfterDays,
        // Why the lesson is locked: enrollment, drip or sequence; null when it is open
        String lockReason,
        // When a dripped lesson opens for this learner; null otherwise
        LocalDate availableOn
) {
}
