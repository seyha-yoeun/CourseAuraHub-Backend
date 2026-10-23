package com.course_aura_hub.enrollment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EnrollmentResponse(
        Integer id,
        Integer studentId,
        String studentName,
        Integer courseId,
        String courseTitle,
        String courseSlug,
        LocalDate enrolledAt,
        BigDecimal amount
) {
}
