package com.course_aura_hub.audit.dto;

import java.util.List;

public record AdminAuditPageResponse(
        List<AdminAuditEventResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
