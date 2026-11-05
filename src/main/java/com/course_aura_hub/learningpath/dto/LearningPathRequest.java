package com.course_aura_hub.learningpath.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create or update a path. On update, null fields keep their value.
 *
 * @param courseSlugs the path's courses in order; replaces the current list
 */
public record LearningPathRequest(
        // Required when creating
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,
        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,
        @Size(max = 1000, message = "Thumbnail URL must not exceed 1000 characters")
        String thumbnail,
        // draft or published
        String status,
        @Size(max = 30, message = "A path holds at most 30 courses")
        List<@NotBlank(message = "Course slug is required") String> courseSlugs
) {
}
