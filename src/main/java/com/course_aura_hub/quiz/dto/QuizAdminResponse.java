package com.course_aura_hub.quiz.dto;

import java.util.List;

/** A quiz with its answers, for admins building it. */
public record QuizAdminResponse(Integer lessonId, int passPercent, List<Question> questions) {

    public record Question(Integer id, String prompt, String explanation, List<Option> options) {
    }

    public record Option(Integer id, String text, boolean correct) {
    }
}
