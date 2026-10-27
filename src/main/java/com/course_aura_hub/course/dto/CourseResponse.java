package com.course_aura_hub.course.dto;

import com.course_aura_hub.student.dto.StudentResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CourseResponse(
        Integer id,
        String slug,
        String title,
        String description,
        String instructor,
        BigDecimal price,
        BigDecimal originalPrice,
        String status,
        String category,
        String level,
        String thumbnail,
        BigDecimal rating,
        Integer totalStudents,
        Integer totalLessons,
        BigDecimal totalHours,
        BigDecimal revenue,
        BigDecimal completionRate,
        List<String> tags,
        LocalDate createdAt,
        LocalDate updatedAt,
        List<Integer> monthlyEnrollments,
        List<BigDecimal> monthlyRevenue,
        List<StudentResponse> enrolledStudentList,
        // Whether the caller is enrolled; null on list responses
        Boolean enrolled
) {
}
