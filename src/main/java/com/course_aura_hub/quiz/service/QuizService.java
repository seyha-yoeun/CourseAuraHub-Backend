package com.course_aura_hub.quiz.service;

import com.course_aura_hub.quiz.dto.QuizAdminRequest;
import com.course_aura_hub.quiz.dto.QuizAdminResponse;
import com.course_aura_hub.quiz.dto.QuizResponse;
import com.course_aura_hub.quiz.dto.QuizResultResponse;

import java.util.Map;

public interface QuizService {

    /** The quiz without its answers, for an enrolled learner. */
    QuizResponse findForLearner(String email, Integer lessonId);

    /** Grades an attempt on the server. Passing completes the lesson, which can earn the certificate. */
    QuizResultResponse submit(String email, Integer lessonId, Map<Integer, Integer> answers);

    QuizAdminResponse findForAdmin(Integer lessonId);

    /** Replaces the questions. Past attempts and passes are kept. */
    QuizAdminResponse save(Integer lessonId, QuizAdminRequest request);
}
