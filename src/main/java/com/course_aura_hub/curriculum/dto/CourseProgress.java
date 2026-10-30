package com.course_aura_hub.curriculum.dto;

public record CourseProgress(
        long completedLessons,
        long totalLessons,
        int progressPercent,
        boolean completed
) {

    public static CourseProgress of(long completedLessons, long totalLessons, boolean completed) {
        int percent;
        if (completed) {
            percent = 100;
        } else if (totalLessons <= 0) {
            percent = 0;
        } else {
            percent = (int) Math.min(100, (completedLessons * 100) / totalLessons);
        }
        return new CourseProgress(completedLessons, totalLessons, percent, completed);
    }
}
