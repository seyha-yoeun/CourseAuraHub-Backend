package com.course_aura_hub.live.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Live session shapes. Learners never get the meeting link from a listing, only by joining. */
public final class LiveDtos {

    private LiveDtos() {
    }

    public record SessionRequest(
            @NotBlank(message = "Title is required") @Size(max = 200, message = "Title must not exceed 200 characters")
            String title,
            @Size(max = 2000, message = "Description must not exceed 2000 characters") String description,
            @NotNull(message = "Start time is required") Instant startsAt,
            @NotNull(message = "Duration is required")
            @Min(value = 5, message = "At least 5 minutes") @Max(value = 600, message = "At most 10 hours")
            Integer durationMinutes,
            @NotBlank(message = "Meeting link is required") @Size(max = 1000) String meetingUrl,
            Integer recordingLessonId
    ) {
    }

    /** Staff view, with the meeting link and how many joined. */
    public record ManagedSession(Integer id, String title, String description, Instant startsAt, Instant endsAt,
                                 int durationMinutes, String meetingUrl, Integer recordingLessonId, boolean cancelled,
                                 long attendees) {
    }

    /** @param joinable whether joining is open now, from shortly before the start until the end */
    public record LearnerSession(Integer id, String courseSlug, String courseTitle, String title, String description,
                                 Instant startsAt, Instant endsAt, boolean cancelled, boolean joinable, boolean attended,
                                 Integer recordingLessonId) {
    }

    public record JoinResponse(String meetingUrl) {
    }

    /** @param feedUrl secret: anyone with it can read my schedule; rotating it replaces the old one */
    public record CalendarFeed(String feedUrl) {
    }
}
