package com.course_aura_hub.enrollment.mapper;

import com.course_aura_hub.enrollment.dto.EnrollmentResponse;
import com.course_aura_hub.enrollment.model.Enrollment;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentAssembler {

    public EnrollmentResponse toResponse(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getStudent().getId(),
                enrollment.getStudent().getName(),
                enrollment.getCourse().getId(),
                enrollment.getCourse().getTitle(),
                enrollment.getCourse().getSlug(),
                enrollment.getEnrolledAt(),
                enrollment.getAmount()
        );
    }
}
