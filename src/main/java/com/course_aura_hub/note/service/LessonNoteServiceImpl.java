package com.course_aura_hub.note.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.note.dto.LessonNoteRequest;
import com.course_aura_hub.note.dto.LessonNoteResponse;
import com.course_aura_hub.note.model.LessonNote;
import com.course_aura_hub.note.repository.LessonNoteRepository;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LessonNoteServiceImpl implements LessonNoteService {

    private final LessonNoteRepository noteRepository;
    private final LessonRepository lessonRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final LearnerAccountService learnerAccountService;

    @Override
    @Transactional(readOnly = true)
    public List<LessonNoteResponse> findMine(String email, Integer lessonId) {
        return learnerAccountService.findCurrentStudent(email)
                .map(student -> noteRepository.findByStudentIdAndLessonIdOrderByCreatedAtDescIdDesc(student.getId(), lessonId)
                        .stream().map(this::toResponse).toList())
                .orElseGet(List::of);
    }

    @Override
    @Transactional
    public LessonNoteResponse create(String email, Integer lessonId, LessonNoteRequest request) {
        requireContent(request);
        Lesson lesson = lessonRepository.findDetailedById(lessonId)
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));
        Student student = learnerAccountService.currentStudent(email);
        if (!studentCourseRepository.existsByStudentIdAndCourseId(student.getId(), lesson.getSection().getCourse().getId())) {
            throw new ForbiddenActionException("Enroll in this course to save notes");
        }
        return toResponse(noteRepository.save(LessonNote.builder()
                .studentId(student.getId())
                .lessonId(lessonId)
                .body(clean(request.body()))
                .positionSeconds(request.positionSeconds())
                .build()));
    }

    @Override
    @Transactional
    public LessonNoteResponse update(String email, Integer noteId, LessonNoteRequest request) {
        requireContent(request);
        LessonNote note = requireNote(email, noteId);
        note.setBody(clean(request.body()));
        note.setPositionSeconds(request.positionSeconds());
        return toResponse(note);
    }

    @Override
    @Transactional
    public void delete(String email, Integer noteId) {
        noteRepository.delete(requireNote(email, noteId));
    }

    // Someone else's note reads as missing, so ids can't be probed
    private LessonNote requireNote(String email, Integer noteId) {
        Integer studentId = learnerAccountService.currentStudent(email).getId();
        return noteRepository.findByIdAndStudentId(noteId, studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Note", noteId));
    }

    private void requireContent(LessonNoteRequest request) {
        if (!StringUtils.hasText(request.body()) && request.positionSeconds() == null) {
            throw new BusinessRuleException("Write a note or save a video position");
        }
    }

    private String clean(String body) {
        return StringUtils.hasText(body) ? body.trim() : null;
    }

    private LessonNoteResponse toResponse(LessonNote note) {
        return new LessonNoteResponse(
                note.getId(),
                note.getLessonId(),
                note.getBody(),
                note.getPositionSeconds(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
