package com.course_aura_hub.instructor.dto;

/** Only what a teacher needs: instructors aren't admins, so no contact details or spending. */
public record InstructorStudentResponse(
        Integer id,
        String name,
        String avatar,
        boolean completed
) {
}
