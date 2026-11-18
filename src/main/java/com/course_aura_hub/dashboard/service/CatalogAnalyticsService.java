package com.course_aura_hub.dashboard.service;

import com.course_aura_hub.dashboard.dto.FunnelDtos;

import java.time.LocalDate;
import java.util.UUID;

/** Anonymous catalog events, the admin funnel built from them, and learner retention cohorts. */
public interface CatalogAnalyticsService {

    /**
     * Records one event unless the same visitor sent it for the same course recently.
     *
     * @param userId the signed-in user, or null
     * @return false when the client is over its rate limit
     */
    boolean record(FunnelDtos.EventRequest request, UUID userId, String clientIp, String userAgent);

    /** @param courseSlug blank for every course */
    FunnelDtos.Funnel funnel(LocalDate from, LocalDate to, String courseSlug);

    /** Monthly cohorts by joining month, with the share who completed a lesson in each later month. */
    FunnelDtos.Retention retention(int months);

    /** Deletes events past the retention period. @return how many */
    int prune();
}
