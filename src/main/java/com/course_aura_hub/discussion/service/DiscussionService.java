package com.course_aura_hub.discussion.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.discussion.dto.LessonAnswerResponse;
import com.course_aura_hub.discussion.dto.LessonQuestionResponse;
import com.course_aura_hub.discussion.dto.OpenQuestionPageResponse;

import java.util.List;

public interface DiscussionService {

    List<LessonQuestionResponse> findQuestions(Actor actor, Integer lessonId);

    LessonQuestionResponse ask(Actor actor, Integer lessonId, String body);

    LessonAnswerResponse answer(Actor actor, Integer questionId, String body);

    LessonQuestionResponse setResolved(Actor actor, Integer questionId, boolean resolved);

    void deleteQuestion(Actor actor, Integer questionId);

    void deleteAnswer(Actor actor, Integer answerId);

    /** Unresolved questions: every course for admins, their own for instructors. */
    OpenQuestionPageResponse findOpen(Actor actor, int page, int size);
}
