package com.course_aura_hub.learner.service;

import com.course_aura_hub.learner.dto.LearningStreakResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LearningStreakCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    @Test
    void noActivity_hasNoStreak() {
        assertThat(LearningStreakCalculator.calculate(List.of(), TODAY))
                .isEqualTo(new LearningStreakResponse(0, 0, false));
    }

    @Test
    void consecutiveDaysEndingToday_countAsCurrentStreak() {
        LearningStreakResponse streak = LearningStreakCalculator.calculate(
                List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2)), TODAY);

        assertThat(streak).isEqualTo(new LearningStreakResponse(3, 3, true));
    }

    @Test
    void streakEndingYesterday_isStillCurrent() {
        LearningStreakResponse streak = LearningStreakCalculator.calculate(
                List.of(TODAY.minusDays(1), TODAY.minusDays(2)), TODAY);

        assertThat(streak).isEqualTo(new LearningStreakResponse(2, 2, false));
    }

    @Test
    void missedDay_breaksCurrentStreakButKeepsLongest() {
        LearningStreakResponse streak = LearningStreakCalculator.calculate(
                List.of(TODAY.minusDays(2), TODAY.minusDays(5), TODAY.minusDays(6), TODAY.minusDays(7), TODAY.minusDays(8)),
                TODAY);

        assertThat(streak).isEqualTo(new LearningStreakResponse(0, 4, false));
    }

    @Test
    void duplicateDays_countOnce() {
        LearningStreakResponse streak = LearningStreakCalculator.calculate(
                List.of(TODAY, TODAY, TODAY.minusDays(1)), TODAY);

        assertThat(streak).isEqualTo(new LearningStreakResponse(2, 2, true));
    }
}
