package com.course_aura_hub.learner.service;

import com.course_aura_hub.learner.dto.LeaderboardDtos;

/** XP leaderboards and weekly lesson goals. XP is derived from activity, never stored. */
public interface LeaderboardService {

    /**
     * Ranks learners by XP earned since the start of this week or month (UTC): 10 per lesson completed, 20 per quiz
     * passed and 100 per certificate. Hidden learners and deleted accounts are left out.
     *
     * @param scope      week or month
     * @param courseSlug only count activity in this published course; blank for every course
     */
    LeaderboardDtos.Leaderboard leaderboard(String email, String scope, String courseSlug, int limit);

    LeaderboardDtos.GoalResponse goal(String email, String timeZone);

    LeaderboardDtos.GoalResponse updateGoal(String email, LeaderboardDtos.GoalRequest request, String timeZone);
}
