package com.course_aura_hub.learner.service;

import com.course_aura_hub.learner.dto.AchievementResponse;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Aggregates a learner's activity into stats, streaks and achievements.
 */
public interface LearnerStatsService {

    /**
     * Lifetime stats for a learner record, with calendar days resolved in {@code zone}.
     */
    LearnerStats compute(Integer studentId, ZoneId zone, LocalDate today);

    /**
     * Every achievement with the learner's progress toward it, in catalogue order.
     */
    List<AchievementResponse> achievements(LearnerStats stats);
}
