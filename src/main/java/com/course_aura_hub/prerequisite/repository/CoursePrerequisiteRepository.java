package com.course_aura_hub.prerequisite.repository;

import com.course_aura_hub.prerequisite.model.CoursePrerequisite;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CoursePrerequisiteRepository extends JpaRepository<CoursePrerequisite, CoursePrerequisite.Id> {

    @EntityGraph(attributePaths = "requiredCourse")
    List<CoursePrerequisite> findByIdCourseIdOrderByPositionAsc(Integer courseId);

    @Modifying
    @Query("delete from CoursePrerequisite p where p.id.courseId = :courseId")
    void deleteByCourseId(@Param("courseId") Integer courseId);

    /** Every edge, for the cycle check. Small: one row per prerequisite. */
    @Query("select p.id.courseId, p.id.requiredCourseId from CoursePrerequisite p")
    List<Object[]> findAllEdges();
}
