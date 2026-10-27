package com.course_aura_hub.course.mapper;

import com.course_aura_hub.course.dto.CourseResponse;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseMonthlyMetric;
import com.course_aura_hub.student.dto.StudentResponse;
import com.course_aura_hub.student.mapper.StudentAssembler;
import com.course_aura_hub.student.model.Student;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CourseAssembler {

    private static final int MONTHS_IN_YEAR = 12;

    private final StudentAssembler studentAssembler;

    public CourseResponse toResponse(Course course, List<Student> enrolledStudents) {
        return toResponse(course, enrolledStudents, null);
    }

    public CourseResponse toResponse(Course course, List<Student> enrolledStudents, Boolean enrolled) {
        Map<Short, CourseMonthlyMetric> metricsByMonth = new LinkedHashMap<>();
        for (CourseMonthlyMetric metric : course.getMonthlyMetrics()) {
            metricsByMonth.put(metric.getMonth(), metric);
        }

        List<Integer> monthlyEnrollments = new ArrayList<>(MONTHS_IN_YEAR);
        List<BigDecimal> monthlyRevenue = new ArrayList<>(MONTHS_IN_YEAR);
        for (short month = 1; month <= MONTHS_IN_YEAR; month++) {
            CourseMonthlyMetric metric = metricsByMonth.get(month);
            monthlyEnrollments.add(metric == null ? 0 : metric.getEnrollments());
            monthlyRevenue.add(metric == null ? BigDecimal.ZERO : metric.getRevenue());
        }

        List<StudentResponse> enrolledStudentList = enrolledStudents == null
                ? null
                : enrolledStudents.stream().map(studentAssembler::toResponse).toList();

        return new CourseResponse(
                course.getId(),
                course.getSlug(),
                course.getTitle(),
                course.getDescription(),
                course.getInstructor(),
                course.getPrice(),
                course.getOriginalPrice(),
                course.getStatus().apiValue(),
                course.getCategory(),
                course.getLevel().apiValue(),
                course.getThumbnail(),
                course.getRating(),
                course.getTotalStudents(),
                course.getTotalLessons(),
                course.getTotalHours(),
                course.getRevenue(),
                course.getCompletionRate(),
                List.copyOf(course.getTags()),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                List.copyOf(monthlyEnrollments),
                List.copyOf(monthlyRevenue),
                enrolledStudentList,
                enrolled
        );
    }
}
