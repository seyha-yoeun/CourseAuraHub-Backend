package com.course_aura_hub.curriculum.service;

import java.time.LocalDate;

/**
 * Why an enrolled learner can't open a lesson yet.
 *
 * @param reason      {@code drip} (it opens on a date) or {@code sequence} (earlier lessons come first)
 * @param availableOn when a dripped lesson opens; null for a sequence lock
 */
public record LessonLock(String reason, LocalDate availableOn) {

    public static final String DRIP = "drip";
    public static final String SEQUENCE = "sequence";
    public static final String ENROLLMENT = "enrollment";
}
