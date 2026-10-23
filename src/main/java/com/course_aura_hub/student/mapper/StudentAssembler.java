package com.course_aura_hub.student.mapper;

import com.course_aura_hub.student.dto.StudentResponse;
import com.course_aura_hub.student.model.Student;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class StudentAssembler {

    public StudentResponse toResponse(Student student) {
        List<Integer> enrolledCourses = student.getCourseEnrollments().stream()
                .map(studentCourse -> studentCourse.getCourse().getId())
                .toList();

        List<Integer> completedCourses = student.getCourseEnrollments().stream()
                .filter(studentCourse -> studentCourse.isCompleted())
                .map(studentCourse -> studentCourse.getCourse().getId())
                .toList();

        return new StudentResponse(
                student.getId(),
                student.getName(),
                student.getEmail(),
                student.getAvatar(),
                enrolledCourses,
                student.getTotalSpent() == null ? BigDecimal.ZERO : student.getTotalSpent(),
                student.getJoinedAt(),
                student.getLastActive(),
                student.getStatus().apiValue(),
                completedCourses,
                student.getCountry()
        );
    }
}
