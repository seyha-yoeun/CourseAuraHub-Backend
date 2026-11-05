package com.course_aura_hub.announcement.service;

import com.course_aura_hub.announcement.dto.AnnouncementPageResponse;
import com.course_aura_hub.announcement.dto.AnnouncementRequest;
import com.course_aura_hub.announcement.dto.AnnouncementResponse;
import com.course_aura_hub.announcement.model.CourseAnnouncement;
import com.course_aura_hub.announcement.repository.CourseAnnouncementRepository;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.service.CourseAccess;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LENGTH = 160;
    private static final String FALLBACK_AUTHOR = "Course team";

    private final CourseAnnouncementRepository announcementRepository;
    private final CourseAccess courseAccess;
    private final StudentCourseRepository studentCourseRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AnnouncementResponse create(Actor actor, String slug, AnnouncementRequest request) {
        Course course = requireManaged(actor, slug);
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessRuleException("Only published courses can send announcements");
        }
        long recent = announcementRepository.countByCourseIdAndCreatedAtAfter(
                course.getId(), Instant.now().minus(Duration.ofDays(1)));
        if (recent >= DAILY_LIMIT) {
            throw new BusinessRuleException(
                    "A course can send at most " + DAILY_LIMIT + " announcements a day");
        }

        CourseAnnouncement saved = announcementRepository.save(CourseAnnouncement.builder()
                .course(course)
                .authorId(actor.userId())
                .authorName(authorName(actor))
                .title(request.title().trim())
                .body(request.body().trim())
                .build());

        List<Integer> studentIds = studentCourseRepository.findStudentIdsByCourseId(course.getId());
        // The link is unique per announcement, so an unread earlier one never suppresses this one
        notificationService.notifyStudentsOnce(
                studentIds,
                NotificationType.ANNOUNCEMENT,
                course.getTitle() + ": " + saved.getTitle(),
                preview(saved.getBody()),
                "/learn/" + course.getSlug() + "?announcement=" + saved.getId()
        );
        log.info("Announcement created id={} courseId={} recipients={}", saved.getId(), course.getId(), studentIds.size());
        return AnnouncementResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AnnouncementPageResponse findForManager(Actor actor, String slug, int page, int size) {
        return page(requireManaged(actor, slug), page, size);
    }

    @Override
    @Transactional
    public void delete(Actor actor, String slug, Long id) {
        Course course = requireManaged(actor, slug);
        CourseAnnouncement announcement = announcementRepository.findByIdAndCourseId(id, course.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Announcement", id));
        announcementRepository.delete(announcement);
        log.info("Announcement deleted id={} courseId={}", id, course.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public AnnouncementPageResponse findForLearner(Actor actor, String slug, int page, int size) {
        Course course = courseAccess.requireCourse(slug);
        boolean staff = courseAccess.canManage(course, actor);
        if (!staff && !studentCourseRepository.isUserEnrolled(actor.email(), course.getId())) {
            throw new ForbiddenActionException("Enroll in this course to read its announcements");
        }
        return page(course, page, size);
    }

    private AnnouncementPageResponse page(Course course, int page, int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
        Page<CourseAnnouncement> result =
                announcementRepository.findByCourseIdOrderByCreatedAtDescIdDesc(course.getId(), request);
        return new AnnouncementPageResponse(
                result.map(AnnouncementResponse::from).getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    private Course requireManaged(Actor actor, String slug) {
        Course course = courseAccess.requireCourse(slug);
        courseAccess.requireManager(course, actor);
        return course;
    }

    private String authorName(Actor actor) {
        return userRepository.findById(actor.userId())
                .map(AnnouncementServiceImpl::fullName)
                .filter(StringUtils::hasText)
                .orElse(FALLBACK_AUTHOR);
    }

    private static String fullName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        return (first + " " + last).trim();
    }

    private static String preview(String body) {
        String flat = body.replaceAll("\\s+", " ");
        return flat.length() <= PREVIEW_LENGTH ? flat : flat.substring(0, PREVIEW_LENGTH - 1) + "…";
    }
}
