package com.course_aura_hub.student.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StudentResponse(
        Integer id,
        String name,
        String email,
        String avatar,
        List<Integer> enrolledCourses,
        BigDecimal totalSpent,
        LocalDate joinedAt,
        LocalDate lastActive,
        String status,
        List<Integer> completedCourses,
        String country,
        // The sign-in account, for account controls. Null for sample learners without one
        UUID userId
) {
}
