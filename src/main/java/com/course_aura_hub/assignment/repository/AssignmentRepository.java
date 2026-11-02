package com.course_aura_hub.assignment.repository;

import com.course_aura_hub.assignment.model.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Integer> {

    Optional<Assignment> findByLessonId(Integer lessonId);
}
