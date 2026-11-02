package com.course_aura_hub.note.service;

import com.course_aura_hub.note.dto.LessonNoteRequest;
import com.course_aura_hub.note.dto.LessonNoteResponse;

import java.util.List;

public interface LessonNoteService {

    List<LessonNoteResponse> findMine(String email, Integer lessonId);

    LessonNoteResponse create(String email, Integer lessonId, LessonNoteRequest request);

    LessonNoteResponse update(String email, Integer noteId, LessonNoteRequest request);

    void delete(String email, Integer noteId);
}
