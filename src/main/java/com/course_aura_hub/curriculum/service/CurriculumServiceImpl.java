package com.course_aura_hub.curriculum.service;

import com.course_aura_hub.certificate.service.CertificateService;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.curriculum.dto.CourseProgress;
import com.course_aura_hub.curriculum.dto.CurriculumResponse;
import com.course_aura_hub.curriculum.dto.LessonCreateRequest;
import com.course_aura_hub.curriculum.dto.LessonResponse;
import com.course_aura_hub.curriculum.dto.LessonUpdateRequest;
import com.course_aura_hub.curriculum.dto.SectionCreateRequest;
import com.course_aura_hub.curriculum.dto.SectionResponse;
import com.course_aura_hub.curriculum.dto.SectionUpdateRequest;
import com.course_aura_hub.curriculum.mapper.CurriculumAssembler;
import com.course_aura_hub.curriculum.model.CourseSection;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.model.LessonType;
import com.course_aura_hub.curriculum.repository.CourseSectionRepository;
import com.course_aura_hub.curriculum.repository.LessonProgressRepository;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.student.model.StudentCourse;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurriculumServiceImpl implements CurriculumService {

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private final CourseRepository courseRepository;
    private final CourseSectionRepository sectionRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final LearnerAccountService learnerAccountService;
    private final CurriculumAssembler curriculumAssembler;
    private final CertificateService certificateService;

    @Override
    @Transactional(readOnly = true)
    public CurriculumResponse getCurriculum(String courseSlug, String viewerEmail, boolean admin) {
        Course course = findCourse(courseSlug);

        // Anonymous visitors have no email, so they can never be enrolled
        Optional<StudentCourse> enrollment = Optional.ofNullable(viewerEmail)
                .flatMap(learnerAccountService::findCurrentStudent)
                .flatMap(student -> studentCourseRepository.findByStudentIdAndCourseId(student.getId(), course.getId()));
        boolean enrolled = enrollment.isPresent();

        if (!admin && !enrolled && course.getStatus() != CourseStatus.PUBLISHED) {
            throw ResourceNotFoundException.of("Course", courseSlug);
        }

        Set<Integer> completedIds = enrollment
                .map(sc -> Set.copyOf(lessonProgressRepository.findCompletedLessonIds(
                        sc.getStudent().getId(), course.getId())))
                .orElse(Set.of());

        List<SectionResponse> sections = sectionRepository.findByCourseIdOrderByPositionAscIdAsc(course.getId())
                .stream()
                .map(section -> curriculumAssembler.toResponse(section, admin || enrolled, completedIds))
                .toList();

        int totalLessons = sections.stream().mapToInt(section -> section.lessons().size()).sum();
        int totalMinutes = sections.stream().mapToInt(SectionResponse::totalDurationMinutes).sum();
        CourseProgress progress = enrollment
                .map(sc -> CourseProgress.of(completedIds.size(), totalLessons, sc.isCompleted()))
                .orElse(null);
        Integer nextLessonId = progress == null || progress.completed() ? null : sections.stream()
                .flatMap(section -> section.lessons().stream())
                .filter(lesson -> !lesson.completed())
                .map(LessonResponse::id)
                .findFirst()
                .orElse(null);

        return new CurriculumResponse(
                course.getId(),
                course.getSlug(),
                enrolled,
                totalLessons,
                totalMinutes,
                progress,
                nextLessonId,
                sections
        );
    }

    @Override
    @Transactional
    public SectionResponse createSection(String courseSlug, SectionCreateRequest request) {
        Course course = findCourse(courseSlug);
        int position = request.position() != null
                ? request.position()
                : sectionRepository.findMaxPosition(course.getId()) + 1;

        CourseSection section = sectionRepository.save(CourseSection.builder()
                .course(course)
                .title(request.title().trim())
                .position(position)
                .build());
        log.info("Created section id={} courseId={}", section.getId(), course.getId());
        return curriculumAssembler.toResponse(section);
    }

    @Override
    @Transactional
    public SectionResponse updateSection(String courseSlug, Integer sectionId, SectionUpdateRequest request) {
        Course course = findCourse(courseSlug);
        CourseSection section = findSection(course, sectionId);

        if (request.title() != null) {
            if (!StringUtils.hasText(request.title())) {
                throw new BusinessRuleException("Section title cannot be blank");
            }
            section.setTitle(request.title().trim());
        }
        if (request.position() != null) {
            section.setPosition(request.position());
        }

        log.info("Updated section id={} courseId={}", section.getId(), course.getId());
        return curriculumAssembler.toResponse(section);
    }

    @Override
    @Transactional
    public void deleteSection(String courseSlug, Integer sectionId) {
        Course course = findCourse(courseSlug);
        CourseSection section = findSection(course, sectionId);
        sectionRepository.delete(section);
        sectionRepository.flush();
        refreshCourseTotals(course);
        log.info("Deleted section id={} courseId={}", sectionId, course.getId());
    }

    @Override
    @Transactional
    public LessonResponse createLesson(String courseSlug, Integer sectionId, LessonCreateRequest request) {
        Course course = findCourse(courseSlug);
        CourseSection section = findSection(course, sectionId);
        int position = request.position() != null
                ? request.position()
                : lessonRepository.findMaxPosition(section.getId()) + 1;

        Lesson lesson = Lesson.builder()
                .section(section)
                .title(request.title().trim())
                .type(LessonType.fromValue(request.type()))
                .durationMinutes(request.durationMinutes() == null ? 0 : request.durationMinutes())
                .contentUrl(trimToNull(request.contentUrl()))
                .preview(Boolean.TRUE.equals(request.preview()))
                .position(position)
                .build();
        section.getLessons().add(lesson);
        Lesson saved = lessonRepository.save(lesson);

        LessonResponse response = curriculumAssembler.toResponse(saved);
        refreshCourseTotals(course);
        log.info("Created lesson id={} sectionId={} courseId={}", saved.getId(), section.getId(), course.getId());
        return response;
    }

    @Override
    @Transactional
    public LessonResponse updateLesson(String courseSlug, Integer lessonId, LessonUpdateRequest request) {
        Course course = findCourse(courseSlug);
        Lesson lesson = lessonRepository.findByIdAndCourseId(lessonId, course.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));

        if (request.title() != null) {
            if (!StringUtils.hasText(request.title())) {
                throw new BusinessRuleException("Lesson title cannot be blank");
            }
            lesson.setTitle(request.title().trim());
        }
        if (request.type() != null) {
            lesson.setType(LessonType.fromValue(request.type()));
        }
        if (request.durationMinutes() != null) {
            lesson.setDurationMinutes(request.durationMinutes());
        }
        if (request.contentUrl() != null) {
            lesson.setContentUrl(trimToNull(request.contentUrl()));
        }
        if (request.preview() != null) {
            lesson.setPreview(request.preview());
        }
        if (request.position() != null) {
            lesson.setPosition(request.position());
        }

        LessonResponse response = curriculumAssembler.toResponse(lesson);
        if (request.durationMinutes() != null) {
            refreshCourseTotals(course);
        }
        log.info("Updated lesson id={} courseId={}", lessonId, course.getId());
        return response;
    }

    @Override
    @Transactional
    public void deleteLesson(String courseSlug, Integer lessonId) {
        Course course = findCourse(courseSlug);
        Lesson lesson = lessonRepository.findByIdAndCourseId(lessonId, course.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));

        // orphanRemoval on CourseSection.lessons deletes the row
        lesson.getSection().getLessons().remove(lesson);
        lessonRepository.flush();
        refreshCourseTotals(course);
        log.info("Deleted lesson id={} courseId={}", lessonId, course.getId());
    }

    /**
     * Keeps the denormalized course totals and every learner's completion flag in sync with the lesson set,
     * and certifies learners the change has just completed (for example when their last unfinished lesson is removed).
     */
    private void refreshCourseTotals(Course course) {
        long lessonCount = lessonRepository.countByCourseId(course.getId());
        long minutes = lessonRepository.sumDurationMinutesByCourseId(course.getId());

        course.setTotalLessons((int) lessonCount);
        course.setTotalHours(BigDecimal.valueOf(minutes).divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP));
        course.setUpdatedAt(LocalDate.now());

        int updated = lessonProgressRepository.recalculateCourseCompletion(course.getId(), lessonCount);
        int certified = certificateService.issueForCompletedLearners(course.getId());
        log.debug("Refreshed course totals courseId={} lessons={} minutes={} learnersRecalculated={} certified={}",
                course.getId(), lessonCount, minutes, updated, certified);
    }

    private Course findCourse(String slug) {
        return courseRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", slug));
    }

    private CourseSection findSection(Course course, Integer sectionId) {
        return sectionRepository.findByIdAndCourseId(sectionId, course.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Section", sectionId));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
