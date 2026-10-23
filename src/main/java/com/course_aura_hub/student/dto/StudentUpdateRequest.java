package com.course_aura_hub.student.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record StudentUpdateRequest(

        @Size(max = 150, message = "Student name must not exceed 150 characters")
        String name,

        @Email(message = "Student email must be valid")
        @Size(max = 255, message = "Student email must not exceed 255 characters")
        String email,

        @Size(max = 1000, message = "Avatar URL must not exceed 1000 characters")
        String avatar,

        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country,

        String status
) {
}
