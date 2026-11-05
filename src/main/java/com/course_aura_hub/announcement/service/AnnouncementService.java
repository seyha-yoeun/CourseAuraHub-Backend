package com.course_aura_hub.announcement.service;

import com.course_aura_hub.announcement.dto.AnnouncementPageResponse;
import com.course_aura_hub.announcement.dto.AnnouncementRequest;
import com.course_aura_hub.announcement.dto.AnnouncementResponse;
import com.course_aura_hub.common.security.Actor;

public interface AnnouncementService {

    /** Most announcements a course can send in a rolling day, so learners aren't spammed. */
    int DAILY_LIMIT = 5;

    /** Saves the announcement and notifies every enrolled learner in the same transaction. */
    AnnouncementResponse create(Actor actor, String slug, AnnouncementRequest request);

    AnnouncementPageResponse findForManager(Actor actor, String slug, int page, int size);

    void delete(Actor actor, String slug, Long id);

    /** For enrolled learners; course staff can read them too. */
    AnnouncementPageResponse findForLearner(Actor actor, String slug, int page, int size);
}
