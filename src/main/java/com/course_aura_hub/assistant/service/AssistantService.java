package com.course_aura_hub.assistant.service;

import com.course_aura_hub.assistant.dto.AssistantDtos;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.quiz.dto.QuizAdminRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * The AI study assistant. It answers from a lesson's study text, only for learners the lesson is open to, and never
 * sees a learner's name or email.
 */
public interface AssistantService {

    AssistantDtos.Source saveSource(Actor actor, Integer lessonId, String body);

    /** Streams the answer as server-sent events: conversation, delta (repeated), then done or error. */
    SseEmitter ask(Actor actor, Integer lessonId, AssistantDtos.AskRequest request);

    /** My recent conversations about this lesson, oldest message first. */
    List<AssistantDtos.Conversation> conversations(Actor actor, Integer lessonId);

    AssistantDtos.Usage usage(Actor actor);

    /** A short summary of the lesson, made once per version of its text. */
    AssistantDtos.Summary summary(Actor actor, Integer lessonId);

    /** Draft quiz questions from the lesson text, for the quiz editor. Nothing is saved or published. */
    List<QuizAdminRequest.Question> draftQuiz(Actor actor, Integer lessonId, int count);
}
