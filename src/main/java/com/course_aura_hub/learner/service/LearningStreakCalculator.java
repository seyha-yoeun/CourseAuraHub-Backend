package com.course_aura_hub.learner.service;

import com.course_aura_hub.learner.dto.LearningStreakResponse;

import java.time.LocalDate;
import java.util.Collection;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Derives daily learning streaks from the calendar days on which a learner completed lessons.
 */
public final class LearningStreakCalculator {

    private LearningStreakCalculator() {
    }

    public static LearningStreakResponse calculate(Collection<LocalDate> activeDays, LocalDate today) {
        NavigableSet<LocalDate> days = new TreeSet<>(activeDays);
        if (days.isEmpty()) {
            return new LearningStreakResponse(0, 0, false);
        }

        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : days) {
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = day;
        }

        // A streak is not broken until a whole day passes without study, so count back from yesterday if needed
        boolean activeToday = days.contains(today);
        int current = 0;
        for (LocalDate day = activeToday ? today : today.minusDays(1); days.contains(day); day = day.minusDays(1)) {
            current++;
        }

        return new LearningStreakResponse(current, longest, activeToday);
    }
}
