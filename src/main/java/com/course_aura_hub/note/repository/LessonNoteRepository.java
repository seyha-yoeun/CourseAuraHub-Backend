package com.course_aura_hub.note.repository;

import com.course_aura_hub.note.model.LessonNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonNoteRepository extends JpaRepository<LessonNote, Integer> {

    List<LessonNote> findByStudentIdAndLessonIdOrderByCreatedAtDescIdDesc(Integer studentId, Integer lessonId);

    Optional<LessonNote> findByIdAndStudentId(Integer id, Integer studentId);
}
