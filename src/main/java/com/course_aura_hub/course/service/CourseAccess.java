package com.course_aura_hub.course.service;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.common.security.SecurityUtils;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.file.model.FilePurpose;
import com.course_aura_hub.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

/** Admins manage every course; instructors only the ones they own. Used from @PreAuthorize as @courseAccess. */
@Component("courseAccess")
@RequiredArgsConstructor
public class CourseAccess {

    private static final String REFERENCE_PREFIX = "storage://";

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public boolean canManage(Authentication authentication, String slug) {
        if (SecurityUtils.isAdmin(authentication)) {
            return true;
        }
        return SecurityUtils.isInstructor(authentication) && courseRepository.findBySlug(slug)
                .filter(course -> owns(course, userIdOf(authentication)))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean canManageLesson(Authentication authentication, Integer lessonId) {
        if (SecurityUtils.isAdmin(authentication)) {
            return true;
        }
        return SecurityUtils.isInstructor(authentication) && lessonRepository.findDetailedById(lessonId)
                .filter(lesson -> owns(lesson.getSection().getCourse(), userIdOf(authentication)))
                .isPresent();
    }

    public boolean canManage(Course course, Actor actor) {
        return actor.admin() || owns(course, actor.userId());
    }

    public void requireManager(Course course, Actor actor) {
        if (!canManage(course, actor)) {
            throw new ForbiddenActionException("You can only manage your own courses");
        }
    }

    /**
     * An instructor may only attach their own uploads, or keep the file a lesson already has. Otherwise they
     * could read another instructor's paid content, or any other private file, through a signed link.
     *
     * @param lessonId the lesson being edited, or null for a new one
     */
    @Transactional(readOnly = true)
    public void requireUsableFile(Authentication authentication, String contentUrl, Integer lessonId) {
        if (SecurityUtils.isAdmin(authentication) || !StringUtils.hasText(contentUrl)) {
            return;
        }
        String stored = fileService.toStoredUrl(contentUrl);
        if (!stored.startsWith(REFERENCE_PREFIX)) {
            return;
        }
        String current = lessonId == null ? null
                : lessonRepository.findById(lessonId).map(Lesson::getContentUrl).orElse(null);
        if (!stored.equals(current)) {
            fileService.requireOwned(stored, authentication.getName(), FilePurpose.LESSON_CONTENT);
        }
    }

    public Course requireCourse(String slug) {
        return courseRepository.findBySlug(slug).orElseThrow(() -> ResourceNotFoundException.of("Course", slug));
    }

    private boolean owns(Course course, UUID userId) {
        return course.getOwnerId() != null && Objects.equals(course.getOwnerId(), userId);
    }

    private UUID userIdOf(Authentication authentication) {
        return authentication.getPrincipal() instanceof AuthUserPrincipal principal ? principal.getUserId() : null;
    }
}
