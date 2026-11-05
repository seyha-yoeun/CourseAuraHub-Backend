package com.course_aura_hub.learningpath.repository;

import com.course_aura_hub.learningpath.model.LearningPath;
import com.course_aura_hub.learningpath.model.LearningPathStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LearningPathRepository extends JpaRepository<LearningPath, Integer> {

    Optional<LearningPath> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<LearningPath> findByStatusOrderByTitleAsc(LearningPathStatus status);

    List<LearningPath> findAllByOrderByUpdatedAtDescIdDesc();

    /** Published paths that contain any of these courses. */
    @Query("""
            select distinct p from LearningPath p join p.courses pc
            where p.status = :status and pc.course.id in :courseIds
            order by p.title
            """)
    List<LearningPath> findByStatusContainingCourses(
            @Param("status") LearningPathStatus status,
            @Param("courseIds") Collection<Integer> courseIds
    );
}
