package com.course_aura_hub.curriculum.mapper;

import com.course_aura_hub.curriculum.dto.LessonResponse;
import com.course_aura_hub.curriculum.dto.SectionResponse;
import com.course_aura_hub.curriculum.model.CourseSection;
import com.course_aura_hub.curriculum.model.Lesson;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CurriculumAssembler {

    /**
     * @param fullAccess     whether the viewer may see content URLs of non-preview lessons
     * @param completedIds   lesson ids the viewer has completed (empty when not enrolled)
     * @param positions      saved video positions by lesson id (empty when not enrolled)
     */
    public SectionResponse toResponse(
            CourseSection section,
            boolean fullAccess,
            Set<Integer> completedIds,
            Map<Integer, Integer> positions
    ) {
        List<LessonResponse> lessons = section.getLessons().stream()
                .map(lesson -> toResponse(
                        lesson, fullAccess, completedIds.contains(lesson.getId()), positions.get(lesson.getId())))
                .toList();
        int duration = section.getLessons().stream()
                .mapToInt(Lesson::getDurationMinutes)
                .sum();
        return new SectionResponse(section.getId(), section.getTitle(), section.getPosition(), duration, lessons);
    }

    public SectionResponse toResponse(CourseSection section) {
        return toResponse(section, true, Set.of(), Map.of());
    }

    public LessonResponse toResponse(Lesson lesson, boolean fullAccess, boolean completed, Integer resumePosition) {
        boolean unlocked = fullAccess || lesson.isPreview();
        return new LessonResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getType().apiValue(),
                lesson.getDurationMinutes(),
                lesson.isPreview(),
                lesson.getPosition(),
                !unlocked,
                unlocked ? lesson.getContentUrl() : null,
                completed,
                resumePosition
        );
    }

    public LessonResponse toResponse(Lesson lesson) {
        return toResponse(lesson, true, false, null);
    }
}
