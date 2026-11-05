package com.course_aura_hub.curriculum.mapper;

import com.course_aura_hub.curriculum.dto.LessonResponse;
import com.course_aura_hub.curriculum.dto.SectionResponse;
import com.course_aura_hub.curriculum.model.CourseSection;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.service.LessonLock;
import com.course_aura_hub.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CurriculumAssembler {

    private final FileService fileService;

    /**
     * @param fullAccess     whether the viewer may see content URLs of non-preview lessons
     * @param completedIds   lesson ids the viewer has completed (empty when not enrolled)
     * @param positions      saved video positions by lesson id (empty when not enrolled)
     * @param locks          pacing locks for an enrolled learner by lesson id (empty for staff and visitors)
     */
    public SectionResponse toResponse(
            CourseSection section,
            boolean fullAccess,
            Set<Integer> completedIds,
            Map<Integer, Integer> positions,
            Map<Integer, LessonLock> locks
    ) {
        List<LessonResponse> lessons = section.getLessons().stream()
                .map(lesson -> toResponse(lesson, fullAccess, completedIds.contains(lesson.getId()),
                        positions.get(lesson.getId()), locks.get(lesson.getId())))
                .toList();
        int duration = section.getLessons().stream()
                .mapToInt(Lesson::getDurationMinutes)
                .sum();
        return new SectionResponse(section.getId(), section.getTitle(), section.getPosition(), duration, lessons);
    }

    public SectionResponse toResponse(CourseSection section) {
        return toResponse(section, true, Set.of(), Map.of(), Map.of());
    }

    public LessonResponse toResponse(Lesson lesson, boolean fullAccess, boolean completed, Integer resumePosition,
                                     LessonLock lock) {
        boolean unlocked = lesson.isPreview() || (fullAccess && lock == null);
        String lockReason = unlocked ? null : lock != null ? lock.reason() : LessonLock.ENROLLMENT;
        return new LessonResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getType().apiValue(),
                lesson.getDurationMinutes(),
                lesson.isPreview(),
                lesson.getPosition(),
                !unlocked,
                unlocked ? fileService.playableUrl(lesson.getContentUrl()) : null,
                completed,
                resumePosition,
                lesson.getReleaseAfterDays(),
                lockReason,
                unlocked || lock == null ? null : lock.availableOn()
        );
    }

    public LessonResponse toResponse(Lesson lesson) {
        return toResponse(lesson, true, false, null, null);
    }
}
