package com.course_aura_hub.live.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.live.dto.LiveDtos;

import java.util.List;
import java.util.UUID;

/** Live classes: scheduling by course staff, joining by enrolled learners, reminders and a calendar feed. */
public interface LiveSessionService {

    List<LiveDtos.ManagedSession> findForCourse(Actor actor, String courseSlug);

    /** Schedules a session and tells enrolled learners. */
    LiveDtos.ManagedSession create(Actor actor, String courseSlug, LiveDtos.SessionRequest request);

    LiveDtos.ManagedSession update(Actor actor, String courseSlug, Integer id, LiveDtos.SessionRequest request);

    /** Cancels it, telling enrolled learners when it hadn't started yet. Attendance is kept. */
    LiveDtos.ManagedSession cancel(Actor actor, String courseSlug, Integer id);

    /** Sessions in my courses that haven't ended, plus the last week's. */
    List<LiveDtos.LearnerSession> findMine(String email);

    /** Records attendance and returns the meeting link, from shortly before the start until the end. */
    LiveDtos.JoinResponse join(String email, Integer id);

    /** Creates or rotates my calendar feed token. */
    LiveDtos.CalendarFeed rotateCalendarToken(UUID userId);

    /** The ICS feed for a token, or null when the token is unknown. */
    String calendar(String token);

    /** Sends the hour and ten-minute reminders that are due. @return how many sessions reminded */
    int sendReminders();
}
