package com.course_aura_hub.enrollment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EnrollmentCreateRequest(

        @NotNull(message = "Student ID is required")
        Integer studentId,

        @NotNull(message = "Course ID is required")
        Integer courseId,

        @DecimalMin(value = "0.00", message = "Enrollment amount cannot be negative")
        BigDecimal amount,

        LocalDate enrolledAt
) {
}
