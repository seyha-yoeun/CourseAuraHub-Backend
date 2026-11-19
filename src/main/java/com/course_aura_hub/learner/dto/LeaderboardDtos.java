package com.course_aura_hub.learner.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.List;

/** Leaderboards and weekly goals. Entries carry a short display name and avatar only, never contact details. */
public final class LeaderboardDtos {

    private LeaderboardDtos() {
    }

    /** @param you whether this entry is the caller */
    public record Entry(int rank, String displayName, String avatar, long xp, long lessonsCompleted, boolean you) {
    }

    /** @param me the caller's own place, or null when they have no XP yet or are hidden from leaderboards */
    public record Leaderboard(String scope, String courseSlug, Instant from, List<Entry> entries, Entry me,
                              boolean visible) {
    }

    /** A null target clears the goal; a null visibility leaves it as it is. */
    public record GoalRequest(
            @Min(value = 1, message = "Aim for at least 1 lesson a week")
            @Max(value = 100, message = "Aim for at most 100 lessons a week")
            Integer weeklyLessonsTarget,
            Boolean leaderboardVisible
    ) {
    }

    /**
     * @param progressPercent lessons this week against the target, capped at 100; 0 without a target
     */
    public record GoalResponse(Integer weeklyLessonsTarget, long lessonsThisWeek, int progressPercent, boolean achieved,
                               Instant weekStartsAt, boolean leaderboardVisible) {
    }
}
