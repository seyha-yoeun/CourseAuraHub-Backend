package com.course_aura_hub.assistant.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import java.util.List;

/** The JSON shape Claude fills for quiz drafts; the SDK derives the schema from these classes. */
public class QuizDraftOutput {

    @ArraySchema(minItems = 1)
    public List<Question> questions;

    public static class Question {

        @JsonPropertyDescription("The question, answerable from the lesson text alone.")
        public String prompt;

        @JsonPropertyDescription("Why the correct option is right, citing the lesson.")
        public String explanation;

        @ArraySchema(minItems = 2, maxItems = 5)
        public List<Option> options;
    }

    public static class Option {

        public String text;

        @JsonPropertyDescription("True for the one correct option.")
        public boolean correct;
    }
}
