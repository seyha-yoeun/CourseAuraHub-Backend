package com.course_aura_hub.learningpath.repository;

import com.course_aura_hub.learningpath.model.LearningPathCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningPathCertificateRepository extends JpaRepository<LearningPathCertificate, Integer> {

    Optional<LearningPathCertificate> findByPathIdAndStudentId(Integer pathId, Integer studentId);

    Optional<LearningPathCertificate> findByCertificateNumber(String certificateNumber);
}
