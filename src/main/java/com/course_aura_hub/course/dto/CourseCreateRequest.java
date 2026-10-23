package com.course_aura_hub.course.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CourseCreateRequest(

        @NotBlank(message = "Course title is required")
        @Size(max = 200, message = "Course title must not exceed 200 characters")
        String title,

        @Size(max = 5000, message = "Course description must not exceed 5000 characters")
        String description,

        @Size(max = 150, message = "Instructor name must not exceed 150 characters")
        String instructor,

        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        BigDecimal price,

        @DecimalMin(value = "0.00", message = "Original price cannot be negative")
        BigDecimal originalPrice,

        String status,

        @Size(max = 100, message = "Category must not exceed 100 characters")
        String category,

        String level,

        @Size(max = 1000, message = "Thumbnail URL must not exceed 1000 characters")
        String thumbnail,

        @Min(value = 0, message = "Lesson count cannot be negative")
        Integer totalLessons,

        @DecimalMin(value = "0.00", message = "Course hours cannot be negative")
        BigDecimal totalHours,

        List<@Size(max = 100, message = "Tags must not exceed 100 characters") String> tags
) {
}
