package com.course_aura_hub.curriculum.service;

import com.course_aura_hub.curriculum.dto.CurriculumResponse;
import com.course_aura_hub.curriculum.dto.LessonCreateRequest;
import com.course_aura_hub.curriculum.dto.LessonResponse;
import com.course_aura_hub.curriculum.dto.LessonUpdateRequest;
import com.course_aura_hub.curriculum.dto.SectionCreateRequest;
import com.course_aura_hub.curriculum.dto.SectionResponse;
import com.course_aura_hub.curriculum.dto.SectionUpdateRequest;

public interface CurriculumService {

    CurriculumResponse getCurriculum(String courseSlug, String viewerEmail, boolean admin);

    SectionResponse createSection(String courseSlug, SectionCreateRequest request);

    SectionResponse updateSection(String courseSlug, Integer sectionId, SectionUpdateRequest request);

    void deleteSection(String courseSlug, Integer sectionId);

    LessonResponse createLesson(String courseSlug, Integer sectionId, LessonCreateRequest request);

    LessonResponse updateLesson(String courseSlug, Integer lessonId, LessonUpdateRequest request);

    void deleteLesson(String courseSlug, Integer lessonId);
}
