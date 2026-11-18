package com.course_aura_hub.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The catalog-to-completion funnel and monthly retention cohorts. Aggregates only. */
public final class FunnelDtos {

    private FunnelDtos() {
    }

    /**
     * @param event     view, add_to_cart or checkout_start
     * @param visitorId a random id the browser keeps for anonymous visitors; ignored when signed in
     */
    public record EventRequest(
            @NotBlank(message = "courseSlug is required") @Size(max = 200) String courseSlug,
            @NotBlank(message = "event is required") String event,
            @Size(max = 64) String visitorId
    ) {
    }

    /**
     * @param fromPrevious share of the previous step that reached this one, in percent; null for the first step
     * @param fromFirst    share of the first step, in percent
     */
    public record Step(String key, String label, long count, BigDecimal fromPrevious, BigDecimal fromFirst) {
    }

    public record Funnel(LocalDate from, LocalDate to, String courseSlug, List<Step> steps) {
    }

    /** @param offset months after joining; 0 is the joining month */
    public record CohortMonth(int offset, long active, BigDecimal rate) {
    }

    public record Cohort(String month, long learners, List<CohortMonth> months) {
    }

    public record Retention(int months, List<Cohort> cohorts) {
    }
}
