package com.course_aura_hub.survey.model;

import java.util.List;

/**
 * One custom question, stored as JSON on the survey.
 *
 * @param id      stable key for answers, such as q1
 * @param type    rating (1 to 5), choice (one of the options) or text
 * @param options the choices, for a choice question only
 */
public record SurveyQuestion(String id, String text, String type, List<String> options, boolean required) {
}
