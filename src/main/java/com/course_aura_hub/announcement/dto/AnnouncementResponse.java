package com.course_aura_hub.announcement.dto;

import com.course_aura_hub.announcement.model.CourseAnnouncement;

import java.time.Instant;

public record AnnouncementResponse(
        Long id,
        String courseSlug,
        String title,
        String body,
        String authorName,
        Instant createdAt
) {

    public static AnnouncementResponse from(CourseAnnouncement announcement) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getCourse().getSlug(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getAuthorName(),
                announcement.getCreatedAt()
        );
    }
}
