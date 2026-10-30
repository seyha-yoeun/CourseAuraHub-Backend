package com.course_aura_hub.learner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Overview of the current learner's progress and activity")
public record LearnerDashboardResponse(

        @Schema(description = "Courses I'm enrolled in")
        int enrolledCourses,

        @Schema(description = "Enrolled courses not completed yet")
        int inProgressCourses,

        @Schema(description = "Enrolled courses currently completed")
        int completedCourses,

        @Schema(description = "Completion certificates earned")
        long certificatesEarned,

        @Schema(description = "Lessons completed across all courses")
        long lessonsCompleted,

        @Schema(description = "Total duration of completed lessons, in minutes")
        long minutesLearned,

        LearningStreakResponse streak,

        @Schema(description = "Lessons completed per day for the last seven days, oldest first, including today")
        List<DailyActivityResponse> lastSevenDays,

        @Schema(description = "Most recently studied unfinished course, else the newest unfinished enrollment; null if none")
        MyCourseResponse continueLearning,

        @Schema(description = "Time zone used for days and streaks", example = "Asia/Phnom_Penh")
        String timeZone
) {
}
