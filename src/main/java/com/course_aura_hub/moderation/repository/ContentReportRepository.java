package com.course_aura_hub.moderation.repository;

import com.course_aura_hub.moderation.model.ContentReport;
import com.course_aura_hub.moderation.model.ReportStatus;
import com.course_aura_hub.moderation.model.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContentReportRepository extends JpaRepository<ContentReport, Integer> {

    boolean existsByTargetTypeAndTargetIdAndReporterId(ReportTargetType type, Integer targetId, UUID reporterId);

    Page<ContentReport> findByStatusOrderByCreatedAtAscIdAsc(ReportStatus status, Pageable pageable);

    List<ContentReport> findByTargetTypeAndTargetIdAndStatus(ReportTargetType type, Integer targetId, ReportStatus status);

    long countByTargetTypeAndTargetId(ReportTargetType type, Integer targetId);
}
