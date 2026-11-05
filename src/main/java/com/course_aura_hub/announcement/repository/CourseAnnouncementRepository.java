package com.course_aura_hub.announcement.repository;

import com.course_aura_hub.announcement.model.CourseAnnouncement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface CourseAnnouncementRepository extends JpaRepository<CourseAnnouncement, Long> {

    Page<CourseAnnouncement> findByCourseIdOrderByCreatedAtDescIdDesc(Integer courseId, Pageable pageable);

    Optional<CourseAnnouncement> findByIdAndCourseId(Long id, Integer courseId);

    long countByCourseIdAndCreatedAtAfter(Integer courseId, Instant since);
}
