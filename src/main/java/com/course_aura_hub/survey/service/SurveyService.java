package com.course_aura_hub.survey.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.survey.dto.SurveyDtos;

/** Course feedback surveys: staff set them up, learners answer once, staff see aggregates. */
public interface SurveyService {

    /** The course's survey, or null when it has none. Course managers only. */
    SurveyDtos.SurveyView findForManager(Actor actor, String courseSlug);

    /** Creates or replaces the survey. Changing questions keeps earlier answers to questions that still exist. */
    SurveyDtos.SurveyView save(Actor actor, String courseSlug, SurveyDtos.SurveyRequest request);

    /** Removes the survey and every response to it. */
    void delete(Actor actor, String courseSlug);

    SurveyDtos.Results results(Actor actor, String courseSlug);

    /** The active survey for a course the learner is enrolled in. */
    SurveyDtos.LearnerSurvey findForLearner(String email, String courseSlug);

    void answer(String email, String courseSlug, SurveyDtos.AnswerRequest request);

    /** Response count and NPS for course analytics. */
    SurveyDtos.Feedback feedback(Integer courseId);
}
