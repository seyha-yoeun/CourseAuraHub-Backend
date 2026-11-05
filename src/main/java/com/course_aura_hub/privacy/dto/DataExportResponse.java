package com.course_aura_hub.privacy.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Everything stored about one learner, grouped by area. Rows are plain column maps. */
public record DataExportResponse(
        Instant exportedAt,
        Map<String, Object> account,
        Map<String, Object> learnerProfile,
        Map<String, List<Map<String, Object>>> records
) {
}
