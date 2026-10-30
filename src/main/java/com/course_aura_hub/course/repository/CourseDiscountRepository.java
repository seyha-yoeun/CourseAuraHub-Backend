package com.course_aura_hub.course.repository;

import com.course_aura_hub.course.model.CourseDiscount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseDiscountRepository extends JpaRepository<CourseDiscount, Integer> {

    @EntityGraph(attributePaths = "course")
    List<CourseDiscount> findAllByOrderByCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = "course")
    List<CourseDiscount> findByCourseSlugOrderByCreatedAtDescIdDesc(String slug);

    List<CourseDiscount> findByCourseIdAndActiveTrue(Integer courseId);
}
