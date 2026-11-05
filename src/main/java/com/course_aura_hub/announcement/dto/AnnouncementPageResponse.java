package com.course_aura_hub.announcement.dto;

import java.util.List;

public record AnnouncementPageResponse(
        List<AnnouncementResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
