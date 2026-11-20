package com.course_aura_hub.survey.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Course surveys. Results are aggregates only; no response is ever shown with who gave it. */
public final class SurveyDtos {

    private SurveyDtos() {
    }

    public record QuestionRequest(
            @NotBlank(message = "Question text is required") @Size(max = 300, message = "Questions must not exceed 300 characters")
            String text,
            @NotBlank(message = "Question type is required") String type,
            @Size(max = 8, message = "At most 8 options") List<@NotBlank @Size(max = 100) String> options,
            boolean required
    ) {
    }

    /** @param trigger percent_50 or completion */
    public record SurveyRequest(
            @NotBlank(message = "Choose when learners are asked") String trigger,
            Boolean active,
            @NotNull @Size(max = 10, message = "At most 10 questions") List<@Valid QuestionRequest> questions
    ) {
    }

    public record Question(String id, String text, String type, List<String> options, boolean required) {
    }

    /** The built-in 0-10 question is always first and always required. */
    public record SurveyView(String courseSlug, String trigger, boolean active, String recommendQuestion,
                                 List<Question> questions) {
    }

    /** @param eligible whether the learner has reached the point where they're asked */
    public record LearnerSurvey(SurveyView survey, boolean eligible, boolean answered) {
    }

    public record AnswerRequest(
            @NotNull(message = "Tell us how likely you are to recommend the course")
            @Min(value = 0, message = "Pick 0 to 10") @Max(value = 10, message = "Pick 0 to 10")
            Integer npsScore,
            Map<String, Object> answers
    ) {
    }

    /** @param score promoters minus detractors, in percentage points; null without responses */
    public record Nps(Integer score, long promoters, long passives, long detractors) {
    }

    /**
     * @param distribution counts per rating or per option
     * @param comments     the latest text answers, without who wrote them
     */
    public record QuestionResult(String id, String text, String type, long answered, BigDecimal average,
                                 Map<String, Long> distribution, List<String> comments) {
    }

    public record Results(String courseSlug, long responses, Nps nps, List<QuestionResult> questions) {
    }

    /** For course analytics. */
    public record Feedback(long responses, Integer nps) {
    }
}
