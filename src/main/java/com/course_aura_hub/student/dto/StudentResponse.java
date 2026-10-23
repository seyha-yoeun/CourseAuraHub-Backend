package com.course_aura_hub.student.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        String country
) {
}
