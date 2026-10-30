package com.course_aura_hub.review.repository;

import com.course_aura_hub.review.model.CourseReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourseReviewRepository extends JpaRepository<CourseReview, Integer> {

    @EntityGraph(attributePaths = {"student"})
    Page<CourseReview> findByCourseId(Integer courseId, Pageable pageable);

    @EntityGraph(attributePaths = {"student"})
    Optional<CourseReview> findByCourseIdAndStudentId(Integer courseId, Integer studentId);

    Optional<CourseReview> findByIdAndCourseId(Integer id, Integer courseId);

    long countByCourseId(Integer courseId);

    long countByStudentId(Integer studentId);

    @Query("select avg(r.rating) from CourseReview r where r.course.id = :courseId")
    Double averageRating(@Param("courseId") Integer courseId);
}
