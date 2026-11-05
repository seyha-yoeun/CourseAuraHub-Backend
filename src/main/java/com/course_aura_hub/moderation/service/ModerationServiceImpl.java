package com.course_aura_hub.moderation.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.discussion.model.LessonAnswer;
import com.course_aura_hub.discussion.model.LessonQuestion;
import com.course_aura_hub.discussion.repository.LessonAnswerRepository;
import com.course_aura_hub.discussion.repository.LessonQuestionRepository;
import com.course_aura_hub.moderation.dto.ModerationDtos;
import com.course_aura_hub.moderation.model.ContentReport;
import com.course_aura_hub.moderation.model.ReportStatus;
import com.course_aura_hub.moderation.model.ReportTargetType;
import com.course_aura_hub.moderation.repository.ContentReportRepository;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.review.repository.CourseReviewRepository;
import com.course_aura_hub.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    private static final int EXCERPT_LENGTH = 280;
    private static final int MAX_PAGE_SIZE = 100;

    private final ContentReportRepository reportRepository;
    private final CourseReviewRepository reviewRepository;
    private final LessonQuestionRepository questionRepository;
    private final LessonAnswerRepository answerRepository;
    private final ReviewService reviewService;
    private final NotificationService notificationService;

    /** The bits of a reported item an admin needs to see. */
    private record Target(String courseSlug, String text, String author, boolean hidden) {
    }

    @Override
    @Transactional
    public void report(Actor actor, ModerationDtos.ReportRequest request) {
        ReportTargetType type = parseType(request.targetType());
        Target target = load(type, request.targetId());
        if (target.hidden()) {
            return;
        }
        if (reportRepository.existsByTargetTypeAndTargetIdAndReporterId(type, request.targetId(), actor.userId())) {
            return;
        }
        boolean first = reportRepository.countByTargetTypeAndTargetId(type, request.targetId()) == 0;
        reportRepository.save(ContentReport.builder()
                .targetType(type)
                .targetId(request.targetId())
                .reporterId(actor.userId())
                .reason(request.reason().trim())
                .status(ReportStatus.OPEN)
                .build());
        if (first) {
            notificationService.notifyAdmins(NotificationType.COURSE_REVIEW, "Content reported",
                    "A " + type.name().toLowerCase(Locale.ROOT) + " was reported: " + request.reason().trim(), "/admin/reports");
        }
        log.info("Content reported type={} id={}", type, request.targetId());
    }

    @Override
    @Transactional(readOnly = true)
    public ModerationDtos.Page findOpen(int page, int size) {
        var result = reportRepository.findByStatusOrderByCreatedAtAscIdAsc(ReportStatus.OPEN,
                PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, MAX_PAGE_SIZE))));
        return new ModerationDtos.Page(result.map(this::toResponse).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional
    public ModerationDtos.Report hide(Integer reportId, UUID adminId) {
        ContentReport report = requireOpen(reportId);
        switch (report.getTargetType()) {
            case REVIEW -> reviewRepository.findWithCourseById(report.getTargetId()).ifPresent(review -> {
                review.setHidden(true);
                reviewRepository.flush();
                reviewService.refreshRating(review.getCourse().getId());
            });
            case QUESTION -> questionRepository.findById(report.getTargetId()).ifPresent(question -> question.setHidden(true));
            case ANSWER -> answerRepository.findById(report.getTargetId()).ifPresent(answer -> answer.setHidden(true));
        }
        close(report, ReportStatus.HIDDEN, adminId);
        log.info("Content hidden type={} id={}", report.getTargetType(), report.getTargetId());
        return toResponse(report);
    }

    @Override
    @Transactional
    public ModerationDtos.Report dismiss(Integer reportId, UUID adminId) {
        ContentReport report = requireOpen(reportId);
        close(report, ReportStatus.DISMISSED, adminId);
        return toResponse(report);
    }

    /** Every open report on the same item is settled by one decision. */
    private void close(ContentReport report, ReportStatus status, UUID adminId) {
        Instant now = Instant.now();
        reportRepository.findByTargetTypeAndTargetIdAndStatus(report.getTargetType(), report.getTargetId(), ReportStatus.OPEN)
                .forEach(open -> {
                    open.setStatus(status);
                    open.setResolvedBy(adminId);
                    open.setResolvedAt(now);
                });
    }

    private ContentReport requireOpen(Integer id) {
        ContentReport report = reportRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Report", id));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new BusinessRuleException("This report was already handled");
        }
        return report;
    }

    private ModerationDtos.Report toResponse(ContentReport report) {
        Target target = load(report.getTargetType(), report.getTargetId());
        return new ModerationDtos.Report(report.getId(), report.getTargetType().name().toLowerCase(Locale.ROOT), report.getTargetId(),
                target.courseSlug(), excerpt(target.text()), target.author(), report.getReason(),
                report.getStatus().name().toLowerCase(Locale.ROOT),
                reportRepository.countByTargetTypeAndTargetId(report.getTargetType(), report.getTargetId()), report.getCreatedAt());
    }

    private Target load(ReportTargetType type, Integer id) {
        return switch (type) {
            case REVIEW -> reviewRepository.findWithCourseById(id)
                    .map(review -> new Target(review.getCourse().getSlug(),
                            review.getComment() == null ? review.getRating() + " stars" : review.getComment(),
                            review.getStudent().getName(), review.isHidden()))
                    .orElseThrow(() -> ResourceNotFoundException.of("Review", id));
            case QUESTION -> questionRepository.findDetailedById(id)
                    .map(question -> new Target(question.getLesson().getSection().getCourse().getSlug(), question.getBody(),
                            question.getAuthorName(), question.isHidden()))
                    .orElseThrow(() -> ResourceNotFoundException.of("Question", id));
            case ANSWER -> answerRepository.findById(id)
                    .map(answer -> new Target(courseSlugOf(answer), answer.getBody(), answer.getAuthorName(), answer.isHidden()))
                    .orElseThrow(() -> ResourceNotFoundException.of("Answer", id));
        };
    }

    private static String courseSlugOf(LessonAnswer answer) {
        LessonQuestion question = answer.getQuestion();
        return question.getLesson().getSection().getCourse().getSlug();
    }

    private static String excerpt(String text) {
        return text == null || text.length() <= EXCERPT_LENGTH ? text : text.substring(0, EXCERPT_LENGTH - 1) + "…";
    }

    private static ReportTargetType parseType(String value) {
        try {
            return ReportTargetType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("You can report a review, question or answer");
        }
    }
}
