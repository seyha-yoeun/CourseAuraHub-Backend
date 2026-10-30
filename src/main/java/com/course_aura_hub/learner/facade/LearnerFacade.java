package com.course_aura_hub.learner.facade;

import com.course_aura_hub.learner.dto.AchievementResponse;
import com.course_aura_hub.learner.dto.LearnerDashboardResponse;
import com.course_aura_hub.learner.dto.MyCourseResponse;

import java.util.List;

/**
 * Orchestrates learner flows that span the learner, enrollment, and curriculum modules.
 */
public interface LearnerFacade {

    List<MyCourseResponse> findMyCourses(String email);

    MyCourseResponse enroll(String email, String courseSlug);

    /**
     * Progress overview: course counts, totals, streak, recent activity and where to continue.
     *
     * @param timeZone IANA zone for calendar days (e.g. {@code Asia/Phnom_Penh}); UTC when blank
     */
    LearnerDashboardResponse dashboard(String email, String timeZone);

    /**
     * Every achievement with the learner's progress toward it.
     *
     * @param timeZone IANA zone used for streak days; UTC when blank
     */
    List<AchievementResponse> achievements(String email, String timeZone);
}
