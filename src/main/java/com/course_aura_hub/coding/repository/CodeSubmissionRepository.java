package com.course_aura_hub.coding.repository;

import com.course_aura_hub.coding.model.CodeSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CodeSubmissionRepository extends JpaRepository<CodeSubmission, Long> {

    boolean existsByStudentIdAndLessonIdAndPassedTrue(Integer studentId, Integer lessonId);

    Optional<CodeSubmission> findFirstByStudentIdAndLessonIdOrderByCreatedAtDescIdDesc(Integer studentId, Integer lessonId);
}
