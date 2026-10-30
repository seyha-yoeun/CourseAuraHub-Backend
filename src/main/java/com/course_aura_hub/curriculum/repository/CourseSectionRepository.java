package com.course_aura_hub.curriculum.repository;

import com.course_aura_hub.curriculum.model.CourseSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseSectionRepository extends JpaRepository<CourseSection, Integer> {

    List<CourseSection> findByCourseIdOrderByPositionAscIdAsc(Integer courseId);

    Optional<CourseSection> findByIdAndCourseId(Integer id, Integer courseId);

    @Query("select coalesce(max(s.position), -1) from CourseSection s where s.course.id = :courseId")
    int findMaxPosition(@Param("courseId") Integer courseId);
}
